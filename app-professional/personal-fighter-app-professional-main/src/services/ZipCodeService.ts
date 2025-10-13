import {AddressByCepModel} from "../model/AddressByCepModel";
import axios from "axios";

export const findAddressByCep = async (cep: string): Promise<AddressByCepModel> => {
  let response = {} as AddressByCepModel;
  const url = "https://brasilapi.com.br/api/cep/v1/" + cep;
  await axios.get(url)
    .then(res => {
      response = res.data;
    }).catch(error => {
      response = {...response, error: error.message};
    });
  return response;
};
