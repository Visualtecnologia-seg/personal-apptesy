import React from "react";
import { CRow, CToast, CToastBody, CToaster } from "@coreui/react";
import { FiAlertCircle, FiAlertTriangle, FiCheck, FiWifiOff } from "react-icons/fi";
import { NotificationModel } from "../model/NotificationModel";

interface Props {
  showNotification: boolean;
  notification?: NotificationModel;
}

type Feedback = {
  icon: React.ReactNode;
  backgroundColor: string;
  color: string;
  borderColor: string;
};

const getFeedback = (type?: "offline" | "error" | "alert" | "success"): Feedback => {
  switch (type) {
    case "offline":
      return {
        icon: <FiWifiOff size={22} style={{ marginRight: 10, marginLeft: 20 }} color="#772b35" />,
        backgroundColor: "#fadddd",
        color: "#772b35",
        borderColor: "#f8cfcf",
      };
    case "error":
      return {
        icon: <FiAlertCircle size={22} style={{ marginRight: 10, marginLeft: 20 }} color="#772b35" />,
        backgroundColor: "#fadddd",
        color: "#772b35",
        borderColor: "#f8cfcf",
      };
    case "alert":
      return {
        icon: <FiAlertTriangle size={22} style={{ marginRight: 10, marginLeft: 20 }} color="#815c15" />,
        backgroundColor: "#feefd0",
        color: "#815c15",
        borderColor: "#fde9bd",
      };
    case "success":
      return {
        icon: <FiCheck size={22} style={{ marginRight: 10, marginLeft: 20 }} color="#18603a" />,
        backgroundColor: "#d5f1de",
        color: "#18603a",
        borderColor: "#c4ebd1",
      };
    default:
      return {
        icon: null,
        backgroundColor: "#feefd0",
        color: "#815c15",
        borderColor: "#fde9bd",
      };
  }
};

const NotificationToaster: React.FC<Props> = (props) => {
  const type = props.notification?.type;
  const items = props.notification?.notification ?? [];
  const fb = getFeedback(type);

  return (
    <CToaster position="top-right">
      <CToast
        key="toast"
        style={{ borderWidth: 1, borderColor: fb.borderColor, borderRadius: 8 }}
        show={props.showNotification}
        autohide={type !== "offline"}  // <- sem 'delay'
        fade
      >
        <CToastBody
          style={{
            backgroundColor: fb.backgroundColor,
            color: fb.color,
            fontWeight: "bold",
          }}
        >
          {items.map((msg, idx) => (
            <CRow style={{ marginBottom: 8, marginTop: 8 }} key={idx}>
              {fb.icon}
              {msg}
            </CRow>
          ))}
        </CToastBody>
      </CToast>
    </CToaster>
  );
};

export default NotificationToaster;
