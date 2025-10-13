import React, {createContext, useContext, useState} from "react";
import NotificationToaster from "../components/NotificationToaster";
import {NotificationModel} from "../model/NotificationModel";

interface NotificationContextModel {
  notification: NotificationModel
  showNotification: any;

  setNotification(notification: NotificationModel): any;

  setShowNotification(showNotification: boolean): any,
}

const NotificationContext = createContext<NotificationContextModel>({} as NotificationContextModel);

export const NotificationProvider: React.FC = ({children}) => {
  const [showNotification, setShowNotification] = useState<boolean>(false);
  const [notification, setNotification] = useState<NotificationModel>({} as NotificationModel);

  return (
    <NotificationContext.Provider value={{
      notification: notification,
      setNotification: setNotification,
      showNotification: showNotification,
      setShowNotification: setShowNotification,
    }}>
      {children}
      <NotificationToaster showNotification={showNotification} notification={notification}/>
    </NotificationContext.Provider>
  );
};

export function useNotification(): NotificationContextModel {
  const context = useContext(NotificationContext);

  if (!context) {
    throw new Error("useNotification must be used within a UserContextProvider");
  }
  return context;
}
