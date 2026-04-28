package io.github.yagogefaell.asaltamontes.infrastructure.google;

import org.springframework.stereotype.Component;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;

import io.github.yagogefaell.asaltamontes.common.exceptions.InvalidTokenException;
import java.util.Collections;

@Component
public class GoogleTokenVerifier {

    private final GoogleProperties googleProperties;

    public GoogleTokenVerifier(GoogleProperties googleProperties) {
        this.googleProperties = googleProperties;
    }

    public GoogleIdToken.Payload verify(String token) {
        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(), new GsonFactory())
                .setAudience(Collections.singletonList(googleProperties.getClientId()))
                .build();
        
        try {
            GoogleIdToken idToken = verifier.verify(token);
            if (idToken == null) throw new InvalidTokenException("Token de Google inválido");
            return idToken.getPayload();
        } catch (Exception e) {
            throw new InvalidTokenException("Error al verificar token con Google");
        }
    }
}
