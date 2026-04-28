package io.github.yagogefaell.asaltamontes.infrastructure.google;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import io.github.yagogefaell.asaltamontes.common.exceptions.InvalidTokenException;
import org.springframework.stereotype.Service;

@Service
public class GoogleAuthService {
    private final GoogleProperties props;

    public GoogleAuthService(GoogleProperties props) {
        this.props = props;
    }

    public String exchangeCodeForIdToken(String code) {
        try {
            GoogleTokenResponse tokenResponse = new GoogleAuthorizationCodeTokenRequest(
                    new NetHttpTransport(),
                    new GsonFactory(),
                    "https://oauth2.googleapis.com/token",
                    props.getClientId(),
                    props.getClientSecret(),
                    code,
                    "postmessage"
            ).execute();

            return tokenResponse.getIdToken();

        } catch (Exception e) {
            throw new InvalidTokenException("Error al realizar el intercambio de código con Google: " + e.getMessage());
        }
    }
}