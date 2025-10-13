import {get, post} from "./Api";

export const getProducts = async (): Promise<any> => {
  const params = {page: 0, sort: "name,asc", active: true, size: 20};
  return await get("/products", params);
};

export const getProfessionalProducts = async (id: string): Promise<any> => {
  return await get("/products/users/" + id);
};

export const saveProducts = async (data: any, id: string): Promise<any> => {
  return await post("/products/users/" + id, data);
};


