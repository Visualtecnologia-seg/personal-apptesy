import {get, put} from "./Api";
import dayjs from "dayjs";

export const getAgendas = async (id: string): Promise<any> => {
  return await get("/agendas/available/product/" + id);
};

export const getProfessionalAgenda = async (id: string, date: Date): Promise<any> => {
  let today = dayjs(date).format("YYYY-MM-DD").toString();
  return await get("/agendas/user/" + id + "/date/" + today);
};

export const updateAgenda = async (data: any): Promise<any> => {
  return await put("/agendas", data);
};