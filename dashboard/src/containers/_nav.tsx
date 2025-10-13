import React from "react";
import {FiEdit3, FiInfo, FiList, RiBoxingLine, RiUserLine} from '../react-icons';

export default [
  {
    _tag: "CSidebarNavItem",
    name: "Produtos",
    to: "/products",
    icon: <FiList size={22} style={{marginRight: 10}}/>,
  },
  {
    _tag: "CSidebarNavItem",
    name: "Informativos",
    to: "/infos",
    icon: <FiInfo size={22} style={{marginRight: 10}}/>,
  },
  {
    _tag: "CSidebarNavItem",
    name: "Alunos",
    to: "/customers",
    icon: <RiUserLine size={22} style={{marginRight: 10}}/>,
  },
  {
    _tag: "CSidebarNavItem",
    name: "Profissionais",
    to: "/professionals",
    icon: <RiBoxingLine size={22} style={{marginRight: 10}}/>,
  },
];




