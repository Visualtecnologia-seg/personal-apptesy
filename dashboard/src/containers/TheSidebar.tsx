import React, {useState} from "react";
import {useDispatch} from "react-redux";
import {useTypedSelector} from "../store";
import {
  CCol,
  CCreateElement,
  CSidebar,
  CSidebarBrand,
  CSidebarNav,
  CSidebarNavDivider,
  CSidebarNavDropdown,
  CSidebarNavItem,
  CSidebarNavTitle,
} from "@coreui/react";

import navigation from "./_nav";
import {RiLock2Line} from "../react-icons";
import ChangeUserPasswordForm from "../components/ChangeUserPasswordForm";

const TheSidebar = () => {
  const dispatch = useDispatch();
  const show = useTypedSelector((state) => state.sidebarShow);
  const [showModal, setShowModal] = useState<boolean>(false);

  // @ts-ignore
  let nav = navigation;

  return (
    <>
      <CSidebar
        show={show}
        onShowChange={(val: boolean) => dispatch({type: "set", sidebarShow: val})}
      >
        <CSidebarBrand className="d-md-down-none">
          <CCol>
            <h2 style={{color: "#ffffff", marginTop: 20, fontFamily: "Montserrat"}}>Personal</h2>
            <h6 style={{color: "#ffffff", marginBottom: 20, fontFamily: "Montserrat"}}>Administração</h6>
          </CCol>
        </CSidebarBrand>
        <CSidebarNav>
          <CCreateElement
            items={nav}
            components={{
              CSidebarNavDivider,
              CSidebarNavDropdown,
              CSidebarNavItem,
              CSidebarNavTitle,
            }}
          />
          <CSidebarNavItem name="Alterar senha"
                           icon={<RiLock2Line size={22} style={{marginRight: 10}}/>}
                           className="c-d-md-down-none"
                           onClick={() => setShowModal(true)}/>
        </CSidebarNav>
      </CSidebar>
      <ChangeUserPasswordForm showModal={showModal} setShowModal={() => setShowModal(!showModal)}/>
    </>
  );
};

export default React.memo(TheSidebar);
