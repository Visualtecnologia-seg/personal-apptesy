import {UserModel} from "./UserModel";

export interface NotificationModel {
  from: UserModel;
  to: UserModel;
  body: string;
  data?: string;
  fromRole: "CUSTOMER" | "PROFESSIONAL";
  toRole: "CUSTOMER" | "PROFESSIONAL";
}
