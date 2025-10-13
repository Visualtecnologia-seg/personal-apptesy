import {api} from "./Api";
import {CredentialsModel} from "../model/CredentialsModel";
import HandleErrors from "./Exceptions/HandleExceptions";

export const authenticate = async (credentials: CredentialsModel) => {
  let response: any = {};
    await api
        .post("/auth", credentials, {
              headers: {
                      "Content-Type": "application/json",
                            },
                                })
                                    .then(res => {
                                          response.token = res.data;
                                                localStorage.setItem("@blueshark-personal-token", JSON.stringify(res.data));
                                                      return getAuthUser(response.token);
                                                          })
                                                              .then(res => {
                                                                    if (res.error) {
                                                                            response.token = null;
                                                                                    response.error = res.error;
                                                                                            localStorage.removeItem("@blueshark-personal-token");
                                                                                                  } else {
                                                                                                          response.data = res.data;
                                                                                                                  localStorage.setItem("@blueshark-personal-user", JSON.stringify(res.data));
                                                                                                                        }
                                                                                                                            })
                                                                                                                                .catch(error => {
                                                                                                                                      response.error = {type: "alert", notification: HandleErrors(error)};
                                                                                                                                          });
                                                                                                                                            return response;
                                                                                                                                            };

                                                                                                                                            const getAuthUser = async (token: string) => {
                                                                                                                                              let response: any = [];
                                                                                                                                                await api
                                                                                                                                                    .get("/auth/dashboard", {
                                                                                                                                                          headers: {
                                                                                                                                                                  "Content-Type": "application/json",
                                                                                                                                                                          Authorization: "Bearer " + token,
                                                                                                                                                                                },
                                                                                                                                                                                    })
                                                                                                                                                                                        .then(res => {
                                                                                                                                                                                              response.data = res.data;
                                                                                                                                                                                                  })
                                                                                                                                                                                                      .catch(error => {
                                                                                                                                                                                                            response.error = {type: "alert", notification: HandleErrors(error)};
                                                                                                                                                                                                                });
                                                                                                                                                                                                                  return response;
                                                                                                                                                                                                                  };


                                                                                                                                                                                                                  export const putPassword = async (data?: any) => {
                                                                                                                                                                                                                    const token = JSON.parse(localStorage.getItem("@blueshark-personal-token") as string);
                                                                                                                                                                                                                      let response: any = {};
                                                                                                                                                                                                                        await api
                                                                                                                                                                                                                            .put("/auth/new-password", data, {
                                                                                                                                                                                                                                  headers: {
                                                                                                                                                                                                                                          "Content-Type": "application/json",
                                                                                                                                                                                                                                                  Authorization: "Bearer " + token,
                                                                                                                                                                                                                                                        },
                                                                                                                                                                                                                                                            })
                                                                                                                                                                                                                                                                .then(res => {
                                                                                                                                                                                                                                                                      response.data = res.data;
                                                                                                                                                                                                                                                                          })
                                                                                                                                                                                                                                                                              .catch(error => {
                                                                                                                                                                                                                                                                                    response.error = {type: "alert", notification: HandleErrors(error)};
                                                                                                                                                                                                                                                                                        });
                                                                                                                                                                                                                                                                                          return response;
                                                                                                                                                                                                                                                                                          };
                                                                                                                                                                                                                                                                                          