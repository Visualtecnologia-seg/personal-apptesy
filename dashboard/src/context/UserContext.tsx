import React, {createContext, useContext, useState} from "react";
import {UserModel} from "../model/UserModel";

type UserContextData = {
  user: UserModel,
  setUser(user: UserModel): any,
  clearUser: any
}

const UserContext = createContext<UserContextData>({} as UserContextData);

export const UserProvider: React.FC = ({children}) => {
  // TODO Rever esse código
  // const localUser = localStorage.getItem("@blueshark-personal-user");
  // let author = {} as UserModel;
  // if (!localUser) {
  //   author = JSON.parse(localUser).author;
  // }

  const [user, setUser] = useState<UserModel>({});

  return (
    <UserContext.Provider value={{user: user, setUser: setUser, clearUser: () => setUser({})}}>
      {children}
    </UserContext.Provider>
  );
};

export function useUser(): UserContextData {
  const context = useContext(UserContext);

  if (!context) {
    throw new Error("useCustomer must be used within a UserContextProvider");
  }
  return context;
}
