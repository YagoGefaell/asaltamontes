import { useGoogleLogin } from '@react-oauth/google';
import './GoogleLoginButton.css'; // Crearemos este archivo

export function GoogleLoginButton() {
  const login = useGoogleLogin({
    onSuccess: tokenResponse => console.log(tokenResponse),
    onError: error => console.log('Login Failed', error),
  });

  return (
    <button className="btn-google-custom" onClick={() => login()}>
      <img src="/icons/google.svg" alt="Google" className="google-icon" />
      <span>Continuar con Google</span>
    </button>
  );
}