import React from "react";
import {useDispatch} from "react-redux";
import {useTypedSelector} from "../store";
import {CHeader, CHeaderNavLink, CToggler} from "@coreui/react";
import {RiLogoutBoxLine} from "react-icons/ri";

const TheHeader = () => {
  const dispatch = useDispatch();
  const sidebarShow = useTypedSelector((state) => state.sidebarShow);

  const toggleSidebar = () => {
    const val = [true, "responsive"].includes(sidebarShow)
      ? false
      : "responsive";
    dispatch({type: "set", sidebarShow: val});
  };

  const toggleSidebarMobile = () => {
    const val = [false, "responsive"].includes(sidebarShow)
      ? true
      : "responsive";
    dispatch({type: "set", sidebarShow: val});
  };

  const clearStorage = () => {
    localStorage.removeItem("@blueshark-personal-user");
    localStorage.removeItem("@blueshark-personal-token");
    window.location.href = "#/login";
  };

  return (
    <CHeader withSubheader className="d-flex justify-content-between">
      <CToggler
        inHeader
        className="ml-md-3 d-lg-none"
        onClick={toggleSidebarMobile}
      />
      <CToggler
        inHeader
        className="ml-3 d-md-down-none"
        onClick={toggleSidebar}
      />
      <CHeaderNavLink
        className="mt-auto mb-auto mr-5 ml-auto p-2"
        onClick={() => clearStorage()}
      >
        <RiLogoutBoxLine size={22} style={{marginRight: 10}}/>
        Sair
      </CHeaderNavLink>
    </CHeader>
  );
};

export default TheHeader;
