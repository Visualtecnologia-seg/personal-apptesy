import React from "react";
const Products = React.lazy(() => import("./views/product/ProductView"));
const Info = React.lazy(() => import("./views/info/InfoView"));
const Customers = React.lazy(() => import("./views/user/CustomersView"));
const Professionals = React.lazy(() => import("./views/user/ProfessionalsView"));


const routes = [
  {path: "/products", name: "Produtos", component: Products},
  {path: "/infos", name: "Info", component: Info},
  {path: "/customers", name: "Alunos", component: Customers},
  {path: "/professionals", name: "Profissionais", component: Professionals},
];

export default routes;
