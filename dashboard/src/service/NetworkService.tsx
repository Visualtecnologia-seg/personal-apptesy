import {api} from "./Api";

export const getStatus = async () => {
  let response: any = [];
  await api
    .get("/actuator/health")
    .then(res => {
      response = "online";
    })
    .catch(error => {
      response = "offline";
    });
  return response;
};
