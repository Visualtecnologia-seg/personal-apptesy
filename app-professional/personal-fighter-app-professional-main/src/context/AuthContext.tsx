import React, {createContext, useContext, useEffect, useState} from "react";
import * as auth from "../services/AuthService";
import * as SecureStore from "expo-secure-store";
import {UserModel} from "../model";

interface AuthCredentials {
  username: string;
  password: string;
}

interface AuthResponse {
  jwtToken?: string | undefined;
  error?: string | undefined,
}

interface AuthContextData {
  isSignedIn: boolean;
  token: string | null;
  error: string,
  user: UserModel,

  setError(value: string | null): void,

  signIn(data: AuthCredentials): Promise<AuthResponse>;

  signOut(): void;

  setUser(user: UserModel),
}

const AuthContext = createContext<AuthContextData>({} as AuthContextData);

const AuthProvider: React.FC = ({children}) => {
  const [token, setToken] = useState<string | null>(null);
  const [error, setError] = useState<string>("");
  const [user, setUser] = useState<UserModel>({} as UserModel);

  useEffect(() => {
    SecureStore.getItemAsync("personal-token")
      .then(res => {
        if (res) {
          setToken(res);
        }
      });
    SecureStore.getItemAsync("personal-user")
      .then(res => {
        const user = JSON.parse(res);
        setUser(user);
      })
  }, []);

  const signIn = async (data: AuthCredentials) => {
    const res: any = await auth.authenticate(data);
    if (res?.token && res.data) {
      setToken(res.token);
      setUser(res.data);
    }
    return res;
  };

  async function signOut() {
    await SecureStore.deleteItemAsync("personal-token");
    await SecureStore.deleteItemAsync("personal-user");
    setUser({} as UserModel);
    setToken(null);
  }

  return (
    <AuthContext.Provider
      value={{isSignedIn: !!token, token, error, setError, user, setUser, signIn, signOut}}>
      {children}
    </AuthContext.Provider>
  );
};

function useAuth(): AuthContextData {
  const context = useContext(AuthContext);

  if (!context) {
    throw new Error("useAuth must be used within a AuthProvider");
  }
  return context;
}

export {AuthProvider, useAuth};
