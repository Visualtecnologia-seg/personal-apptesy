import {Alert} from "react-native";

export const HandleExceptions = (error) => {
  if (error.response) {
    const e = error.response.data.message;
    console.error(e);
    switch (e) {
      case "User not found":
        Alert.alert("", "Usuário não cadastrado.");
        return e;
      case "User disabled":
        Alert.alert("", "Usuário bloqueado.");
        return e;
      case "Invalid credentials":
        Alert.alert("", "Senha incorreta.");
        return e;
      case "Access Denied":
        Alert.alert("", "Acesso negado. Faça o login novamente.");
        return e;
      case "Invalidated Session":
        Alert.alert("", "Sessão invalidada.");
        return e;
      case "Object not found.":
        Alert.alert("", "Item não encontrado.");
        return e;
      default:
        // Alert.alert("", "Request failed with status code 400.");
        return e;
    }
  } else if (error.request) {
    const e = error.message;
    console.error(e);
    switch (e) {
      case "Network Error":
        // Alert.alert("", "Erro de conexão com internet.");
        return e;
      case "Timeout":
        // Alert.alert("", "Erro de conexão com internet (timeout).");
        return e;
      case "Request failed with status code 400":
        // Alert.alert("", "Request failed with status code 400.");
        return e;
      default:
        // Alert.alert("", "Erro desconhecido");
        return e;
    }
  } else {
    console.error("Error", error?.message);
    return "Error";
  }
};
