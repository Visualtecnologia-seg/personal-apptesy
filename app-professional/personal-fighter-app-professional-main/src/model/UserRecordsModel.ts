import {UserModel} from "./UserModel";

export interface UserRecordsModel {
  id?: string;
  deviceBrand?: string;
  deviceModel?: string;
  deviceModelId?: string;
  deviceOsName?: string;
  deviceOsVersion?: string;
  customerExpoPushNotificationToken?: string;
  professionalExpoPushNotificationToken?: string;
  lastAccessDate?: string;
  user?: UserModel
}