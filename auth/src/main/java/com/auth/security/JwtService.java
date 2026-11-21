package com.auth.security;

import com.auth.config.JwtProperties;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class JwtService {

    private static final String ROLES_CLAIM = "roles";

    private final JwtProperties jwtProperties;

    public String generateToken(UserDetails userDetails) {
        Date now = new Date();
        Date expiresAt = new Date(now.getTime() + jwtProperties.getExpirationMs());

        List<String> roles = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject(userDetails.getUsername())
                .claim(ROLES_CLAIM, roles)
                .issueTime(now)
                .expirationTime(expiresAt)
                .jwtID(UUID.randomUUID().toString())
                .build();

        SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS512), claimsSet);

        try {
            signedJWT.sign(new MACSigner(secretKeyBytes()));
        } catch (JOSEException e) {
            log.error("Failed to sign JWT", e);
            throw new IllegalStateException("Could not generate authentication token", e);
        }

        return signedJWT.serialize();
    }

    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            if (!signedJWT.verify(new MACVerifier(secretKeyBytes()))) {
                return false;
            }

            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
            boolean usernameMatches = userDetails.getUsername().equals(claims.getSubject());
            boolean notExpired = claims.getExpirationTime() != null && claims.getExpirationTime().after(new Date());

            return usernameMatches && notExpired;
        } catch (ParseException | JOSEException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    private JWTClaimsSet parseClaims(String token) {
        try {
            return SignedJWT.parse(token).getJWTClaimsSet();
        } catch (ParseException e) {
            throw new IllegalArgumentException("Malformed JWT token", e);
        }
    }

    private byte[] secretKeyBytes() {
        return Base64.getDecoder().decode(jwtProperties.getSecret());
    }
}
