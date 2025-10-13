const HandleExceptions = error => {
  if (error.response) {
    const e = error.response.data.message;
    console.error(e, error);
    switch (e) {
      case "User not found":
        return ["Usuário não encontrado."];
      case "User disabled":
        return ["Usuário bloqueado."];
      case "Invalid credentials":
        return ["Senha incorreta."];
      case "Access is denied" || "Access danied":
        return ["Acesso negado."];
      case "Invalidated session" || "Invalidated Session":
        return ["Sessão invalidada por outro usuário."];
      case "Could not delete object":
        return ["Não foi possível deletar. Tente novamente."];
      default:
        return ["Erro desconhecido (response)."];
    }
  } else if (error.request) {
    const e = error.message;
    console.error(e, error);
    switch (e) {
      case "Network Error":
        return ["Erro de conexão com o servidor."];
      case "Timeout":
        return ["Erro de conexão com o servidor (timeout)."];
      default:
        return ["Erro desconhecido (request)."];
    }
  } else {
    console.log("Unknown", error)
    return ["Error"];
  }
};
export default HandleExceptions;
