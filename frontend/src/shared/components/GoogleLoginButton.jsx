import { useGoogleLogin } from '@react-oauth/google';
import { FcGoogle } from 'react-icons/fc';
import './GoogleLoginButton.css';

export function GoogleLoginButton({ onLoginSuccess }) {
  const login = useGoogleLogin({
    onSuccess: codeResponse => {
      console.log("Código de autorización recibido:", codeResponse.code);
      onLoginSuccess(codeResponse.code); 
    },
    onError: error => console.log('Login Failed', error),
    flow: 'auth-code', 
  });

  return (
    <button className="btn-google-custom" onClick={() => login()}>
      <FcGoogle size={20} />
      <span>Continuar con Google</span>
    </button>
  );
}