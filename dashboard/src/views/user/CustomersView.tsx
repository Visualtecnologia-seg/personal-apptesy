import React, {useEffect, useState} from "react";
import {CCard, CCardBody, CCardHeader, CDataTable, CImg, CPagination, CRow, CSpinner, CSwitch} from "@coreui/react";

import {UserModel} from "../../model/UserModel";
import {ParamsModel} from "../../model/ParamsModel";
import dayjs from "dayjs";
import noImage from "../../assets/default-avatar.jpg";
import {IoReload} from '../../react-icons';
import {useNotification} from "../../context/NotificationContext";
import {getCustomers, putUser} from "../../service/UserService";

const CustomersView = () => {
  /* Default */
  const [loading, setLoading] = useState<boolean>(true);
  const [firstLoading, setFirstLoading] = useState<boolean>(true);
  /* Notification */
  const {setShowNotification, setNotification} = useNotification();
  /* Table */
  const [params, setParams] = useState<ParamsModel>({size: 10, page: 0, sort: "name,asc"});
  const [totalPages, setTotalPages] = useState<number>(0);
  const [users, setUsers] = useState<UserModel[]>([]);
  const [filter, setFilter] = useState<UserModel>({role: ["CUSTOMER"]} as UserModel);
  const [hasFilter, setHasFilter] = useState<boolean>(false);

  useEffect(() => {
    doGetUsers();
  }, [params, filter]);

  const doGetUsers = () => {
    setLoading(true);
    checkFilters(filter);
    getCustomers(params, filter).then(res => {
      if (res.error) {
        onError(res);
      } else {
        setTotalPages(res?.data?.totalPages);
        setUsers(res?.data?.content);
        setTimeout(() => setFirstLoading(false), 500);
        setTimeout(() => setLoading(false), 1000);
      }
    });
  };

  function onActiveAndInactive(user: UserModel) {
    setLoading(true);
    user.active = !user.active;
    putUser(user).then(res => {
      setShowNotification(false); // Workaround para garantir a mudança no state
      if (res.error) {
        onError(res);
      } else {
        setShowNotification(true);
        setNotification({type: "success", notification: ["Salvo com sucesso"]});
        doGetUsers();
      }
    });
  }

  function checkFilters(f) {
    let result;
    result = Object.values(f).filter(key => {
      return key !== "";
    });
    if (result.length > 0) {
      setHasFilter(true);
    } else {
      setHasFilter(false);
    }
  }

  function onError(res) {
    setShowNotification(false); // Workaround para garantir a mudança no state
    setShowNotification(true);
    setNotification(res.error);
    setTimeout(() => setLoading(false), 1000);
  }

  if (firstLoading) {
    return (
      <div className="d-flex justify-content-center align-items-end" style={{marginTop: 300}}>
        <CSpinner color="primary" style={{width: "4rem", height: "4rem"}}/>
      </div>
    );
  }

  return (
    <>
      {/*<UsersFilter filter={setFilter}/>*/}
      <CCard accentColor="primary">
        <CCardHeader className="table-header">
          <CRow className="justify-content-center align-items-center">
            <div className="flex-grow-1 bs-title" style={{marginLeft: 10}}>
              Alunos {hasFilter && "(com filtros)"}
            </div>
            <IoReload size={22} color={"#45a164"} style={{marginRight: 16}} onClick={() => {
              setLoading(true);
              const emptyFilter: UserModel = {name: "", role: ["CUSTOMER"]};
              setFilter(emptyFilter);
            }}/>
          </CRow>
        </CCardHeader>
        <CCardBody>
          <CDataTable
            loading={loading}
            hover
            striped
            responsive
            size="md"
            items={users}
            fields={["imageUrl", "name", "cpf", "email", "birthday", "active"]}
            itemsPerPageSelect={{
              label: "Itens por página",
              values: [5, 10, 20, 50, 100],
              external: true,
            }}
            onPaginationChange={(size: number) => {
              setParams({...params, size: size});
            }}
            itemsPerPage={params.size}
            pagination
            noItemsViewSlot={<div className="text-center my-5"><h2>Sem conteúdo</h2></div>}
            columnHeaderSlot={{
              imageUrl: "Foto",
              name: "Nome",
              cpf: "CPF",
              email: "E-mail",
              birthday: "Data de Nascimento",
              active: "Ativo?",
              // actions: "Ações"
            }}
            scopedSlots={{
              imageUrl: (item: any) => (
                <td className="m-auto bs-vertical-align-center" style={{width: 70}}>
                  <CImg src={item?.avatarUrl ? item.avatarUrl : noImage}
                        style={{width: 30, height: 30, borderRadius: 15}}/>
                </td>
              ),
              name: (item: any) => <td className="m-auto bs-vertical-align-center">{item?.name}</td>,
              email: (item: any) => <td className="m-auto bs-vertical-align-center">{item?.email}</td>,
              birthday: (item: any) => <td
                className="m-auto bs-vertical-align-center">{dayjs(item?.birthday).format("DD/MM/YYYY")}</td>,
              active: (item: any) => (
                <td className="py-2 bs-vertical-align-center">
                  <CSwitch
                    className={"mx-1"}
                    variant={"3d"}
                    color={"success"}
                    checked={item.active}
                    labelOn={"\u2713"}
                    labelOff={"\u2715"}
                    onChange={() => {
                      onActiveAndInactive(item);
                    }}
                  />
                </td>
              ),
              // actions: (item: any) => (
              //   <td className="py-2 bs-vertical-align-center" style={{width: 120}}>
              //     <div style={{flexDirection: "row"}}>
              //       <CButton
              //         block
              //         color="primary"
              //         className="btn btn-sm"
              //         onClick={() => {}}
              //       > Editar
              //       </CButton>
              //     </div>
              //   </td>
              // ),
            }}
          />
          <CPagination
            activePage={params.page + 1}
            pages={totalPages}
            onActivePageChange={(page: number) =>
              setParams({...params, page: page - 1})
            }
            align="end"
          />
        </CCardBody>
      </CCard>
    </>
  );
};
export default CustomersView;
