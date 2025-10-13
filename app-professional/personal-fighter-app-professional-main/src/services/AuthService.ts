import {api} from "./Api";
import {HandleExceptions} from "./Exceptions/HandleExceptions";
import * as SecureStore from "expo-secure-store";

export const authenticate = async ({username, password}): Promise<any> => {
  let response: any = {};
  await api.post("/auth", {username, password})
    .then(async res => {
      response.token = res.data;
      await SecureStore.setItemAsync("personal-token", res.data);
      return getUser(res.data);
    })
    .then(async res => {
      if (res.error) {
        response.token = null;
        await SecureStore.deleteItemAsync("personal-token");
        response.error = res.error;
      } else {
        await SecureStore.setItemAsync("personal-user", JSON.stringify(res.data));
        response.data = res.data;
      }
    })
    .catch(error => {
      response.error = HandleExceptions(error);
    });
  return response;
};

export const saveUser = async (data: any): Promise<any> => {
  let response: any = {};
  await api
    .post("/users", data)
    .then(res => {
      response.data = res.data;
    })
    .catch(error => {
      response.error = HandleExceptions(error);
    });
  return response;
};

export const getUser = async (token: string) => {
  let response: any = [];
  await api
    .get("/auth/mobile", {
      headers: {
        Authorization: "Bearer " + token
      }
    })
    .then(res => {
      response.data = res.data;
    })
    .catch(error => {
      response.error = HandleExceptions(error);
    });
  return response;
};

export const checkIfExists = async (email?: string): Promise<boolean> => {
  let response: any = {};
  await api
    .get("/auth/check/" + email)
    .then(res => {
      response.data = res.data;
    })
    .catch(error => {
      response.error = HandleExceptions(error);
    });
  return response;
};

export const forgotPassword = async (data: any): Promise<any> => {
  let response: any = {};
  await api
    .post("/auth/forgot-password", data)
    .then(res => {
      response.data = res.data;
    })
    .catch(error => {
      response.error = HandleExceptions(error);
    });
  return response;
};
