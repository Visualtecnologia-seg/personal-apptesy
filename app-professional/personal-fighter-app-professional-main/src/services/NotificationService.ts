import {post} from "./Api";

export const sendNotification = async (data: any) => {
  return await post("/notifications", data);
};
