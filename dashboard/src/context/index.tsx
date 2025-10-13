import React from "react";

import {UserProvider} from "./UserContext";
import {NotificationProvider} from "./NotificationContext";

const AppProvider: React.FC = ({children}) => {
  return (
    <NotificationProvider>
      <UserProvider>{children}</UserProvider>
    </NotificationProvider>
  );
};

export default AppProvider;
