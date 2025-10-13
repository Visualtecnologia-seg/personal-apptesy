import {get} from "./Api";

export const getCustomer = async (id: string): Promise<any> => {
  return await get("/users/" + id);
};
