package com.shun.gateway.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.security.PublicKey;

@Component
public class CheckTokenUtil {

    private static PublicKey PUBLICKEY;


    /**
     * jwt 校验共钥
     */
    @Value("${publicKeyPem}")
    private String publicKeyPem;

    public static final String USER_ID_KEY = "uid";

    @PostConstruct
    public void init() throws IOException {
        Reader rdr = new StringReader(publicKeyPem);
        Object parsed = new org.bouncycastle.openssl.PEMParser(rdr).readObject();
        PUBLICKEY = new JcaPEMKeyConverter().getPublicKey(((SubjectPublicKeyInfo) parsed));
    }

    public Claims check(String token) {
        Claims claims = Jwts.parser()
                .setSigningKey(PUBLICKEY)
                .parseClaimsJws(token)
                .getBody();

        return claims;
    }

}
