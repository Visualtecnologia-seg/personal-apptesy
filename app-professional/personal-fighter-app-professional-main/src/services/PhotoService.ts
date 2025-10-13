import {api, get, remove} from "./Api";
import * as SecureStore from "expo-secure-store";
import {HandleExceptions} from "./Exceptions/HandleExceptions";

export const getProfessionalPhotos = async (id: string): Promise<any> => {
  return await get("/photos/users/" + id);
};

export const postAvatar = async (data: any, id: string): Promise<any> => {
  let response: any = {};
  const token = await SecureStore.getItemAsync("personal-token");
  await api.post("/photos/avatar/users/" + id, data, {
    headers: {
      "Content-Type": "multipart/form-data",
      "Authorization": token
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

export const savePhoto = async (data: any, id: string): Promise<any> => {
  let response: any = {};
  const token = await SecureStore.getItemAsync("personal-token");
  await api.post("/photos/photo/users/" + id, data, {
    headers: {
      "Content-Type": "multipart/form-data",
      "Authorization": token
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

export const removePhoto = async (id: string): Promise<any> => {
  return await remove("/photos/" + id);
};


