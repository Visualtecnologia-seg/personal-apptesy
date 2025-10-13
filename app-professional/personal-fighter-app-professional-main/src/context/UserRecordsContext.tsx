import React, {createContext, useContext, useEffect, useState} from "react";
import {UserRecordsModel} from "../model/UserRecordsModel";
import * as SecureStore from "expo-secure-store";
import {useAuth} from "./AuthContext";
import {saveRecords} from "../services/UserService";

interface UserRecordsContextData {
  userRecords: UserRecordsModel,

  setUserRecords(userRecords: UserRecordsModel),
}

const UserRecordsContext = createContext<UserRecordsContextData>({} as UserRecordsContextData);

export const UserRecordsProvider: React.FC = ({children}) => {
  const {token, user} = useAuth();
  const [userRecords, setUserRecords] = useState({} as UserRecordsModel);

  useEffect(() => {
    SecureStore.getItemAsync("personal-user-records")
      .then(res => {
        if (res) {
          const records = JSON.parse(res);
          setUserRecords(records);
        }
      });
  }, [token, user]);

  return (
    <UserRecordsContext.Provider value={{userRecords, setUserRecords}}>
      {children}
    </UserRecordsContext.Provider>
  );
};

export function useUserRecords(): UserRecordsContextData {
  const context = useContext(UserRecordsContext);

  if (!context) {
    throw new Error("usePushToken must be used within a PushTokenProvider");
  }
  return context;
}
