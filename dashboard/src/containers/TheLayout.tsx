import React from "react";
import {useTypedSelector} from "../store";
import {TheAside, TheContent, TheFooter, TheHeader, TheSidebar} from "./index";
import {Redirect} from "react-router-dom";

const TheLayout = () => {
  const darkMode = useTypedSelector((state) => state.darkMode);
  const classes = `c-app c-default-layout ${darkMode ? "c-dark-theme" : ""}`;
  const user = localStorage.getItem("@blueshark-personal-user");

  if (user === null) {
    return (
      <Redirect from="/" to="/login"/>
    );
  }

  return (
    <div className={classes}>
      <TheSidebar/>
      <TheAside/>
      <div className="c-wrapper">
        <TheHeader/>
        <div className="c-body">
          <TheContent/>
        </div>
        <TheFooter/>
      </div>
    </div>
  );
};

export default TheLayout;
