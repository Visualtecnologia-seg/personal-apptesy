import axios from "axios";
import {HandleExceptions} from "./Exceptions/HandleExceptions";
import * as SecureStore from "expo-secure-store";
import {ReqParamsModel} from "../model/ReqParamsModel";

/* Development */
const local = "http://192.168.0.199:8080";

/* Production */
const awsURL = "http://ec2-3-19-249-21.us-east-2.compute.amazonaws.com:8080";

/* Configs */
const baseURL = awsURL;
const timeout = 1000 * 30;
const api = axios.create({
  baseURL,
  timeout,
  timeoutErrorMessage: "Timeout",
});

async function post(url: string, data?: any) {
  const token = await SecureStore.getItemAsync("personal-token");
  let response: any = {};
  await api
    .post(url, data, {
      headers: {Authorization: token},
    })
    .then(res => {
      response.data = res.data;
    })
    .catch(error => {
      response.error = HandleExceptions(error);
    });
  return response;
}

async function put(url: string, data?: any) {
  const token = await SecureStore.getItemAsync("personal-token");
  let response: any = {};
  await api
    .put(url, data, {
      headers: {Authorization: token},
    })
    .then(res => {
      response.data = res.data;
    })
    .catch(error => {
      response.error = HandleExceptions(error);
    });
  return response;
}

async function get(url: string, params?: ReqParamsModel) {
  let response: any = {};
  let token = await SecureStore.getItemAsync("personal-token");
  await api
    .get(url, {
      params: params,
      headers: {Authorization: token},
    })
    .then(res => {
      if (params) {
        response.pageNumber = res?.data?.pageable?.pageNumber;
        response.totalPages = res?.data?.totalPages - 1;
      }
      response.data = res.data;
    })
    .catch(error => {
      response.error = HandleExceptions(error);
    });
  return response;
}

async function remove(url: string) {
  let response: any = {};
  let token = await SecureStore.getItemAsync("personal-token");
  await api
    .delete(url, {
      headers: {Authorization: token},
    })
    .then(res => {
      response.data = res.data;
    })
    .catch(error => {
      response.error = HandleExceptions(error);
    });
  return response;
}

export {api, baseURL, get, post, put, remove};
