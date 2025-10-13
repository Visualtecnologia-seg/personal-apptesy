import React from "react";
import {AuthProvider} from "./AuthContext";
import {UserRecordsProvider} from "./UserRecordsContext";
import {PushNotificationProvider} from "./PushNotificationContext";


const AppProvider: React.FC = ({children}) => {
  return (
    <AuthProvider>
      <PushNotificationProvider>
          <UserRecordsProvider>
            {children}
          </UserRecordsProvider>
      </PushNotificationProvider>
    </AuthProvider>
  );
};

export default AppProvider;
