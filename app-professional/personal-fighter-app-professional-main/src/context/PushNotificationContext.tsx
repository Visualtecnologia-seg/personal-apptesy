import React, {createContext, useContext, useState} from "react";

interface PushNotificationContextData {
  pushNotification: any,

  setPushNotification(token: string),
}

const PushNotificationContext = createContext<PushNotificationContextData>({} as PushNotificationContextData);

export const PushNotificationProvider: React.FC = ({children}) => {
  const [pushNotification, setPushNotification] = useState("");

  return (
    <PushNotificationContext.Provider
      value={{pushNotification: pushNotification, setPushNotification: setPushNotification}}>
      {children}
    </PushNotificationContext.Provider>
  );
};

export function usePushNotification(): PushNotificationContextData {
  const context = useContext(PushNotificationContext);

  if (!context) {
    throw new Error("usePushToken must be used within a PushTokenProvider");
  }
  return context;
}
