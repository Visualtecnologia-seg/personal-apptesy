export interface NotificationModel {
  notification: string[]
  type: "offline" | "error" | "alert" | "success"
}
