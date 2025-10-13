import {UserModel} from "./UserModel";

export interface AuthModel {
  token?: string,
  user?: UserModel,
}
