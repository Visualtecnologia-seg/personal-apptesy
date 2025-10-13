import {get, post, remove} from "./Api";
import {ProductModel} from "../model/ProductModel";

export const getProducts = async () => {
  return get("/products");
};

export const postProduct = async (data?: ProductModel) => {
  return post("/products", data);
};

export const removeProduct = async (id?: number) => {
  return remove("/products/" + id);
};
