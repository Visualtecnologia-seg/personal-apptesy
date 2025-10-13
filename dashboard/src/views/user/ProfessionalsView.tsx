import React, {useEffect, useState} from "react";
import {
  CButton,
  CCard,
  CCardBody,
  CCardHeader,
  CDataTable,
  CImg,
  CPagination,
  CRow,
  CSpinner,
  CSwitch
} from "@coreui/react";

import {UserModel} from "../../model/UserModel";
import {ParamsModel} from "../../model/ParamsModel";
import noImage from "../../assets/default-avatar.jpg";
import {IoReload} from '../../react-icons';
import {useNotification} from "../../context/NotificationContext";
import {putUser} from "../../service/UserService";
import {FinanceDataModel} from "../../model/FinanceDataModel";
import ConfirmationModal from "../../components/ConfirmationModal";
import {getProfessionalsFinanceData, putFinancePay} from "../../service/FinanceService";

const ProfessionalsView = () => {
  /* Default */
  const [loading, setLoading] = useState<boolean>(true);
  const [firstLoading, setFirstLoading] = useState<boolean>(true);
  /* Notification */
  const {setShowNotification, setNotification} = useNotification();
  /* Table */
  const [params, setParams] = useState<ParamsModel>({size: 10, page: 0, sort: "name,asc"});
  const [totalPages, setTotalPages] = useState<number>(0);
  const [users, setUsers] = useState<FinanceDataModel[]>([]);
  const [filter, setFilter] = useState<FinanceDataModel>({} as FinanceDataModel);
  const [hasFilter, setHasFilter] = useState<boolean>(false);
  /* Confirmation */
  const [showModal, setShowModal] = useState(false);
  const [selectedId, setSelectedId] = useState<number>(0);

  useEffect(() => {
    doGetUsers();
  }, [params, filter]);

  const doGetUsers = () => {
    setLoading(true);
    checkFilters(filter);
    getProfessionalsFinanceData(params, filter).then(res => {
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

  function doPayment() {
    putFinancePay(selectedId).then(() => {
      doGetUsers();
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
              Profissionais {hasFilter && "(com filtros)"}
            </div>
            <IoReload size={22} color={"#45a164"} style={{marginRight: 16}} onClick={() => {
              setLoading(true);
              const emptyFilter = {name: "", age: "", institution: "", grade: ""};
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
            fields={["imageUrl", "name", "email", "bankData", "balance", "payment", "active"]}
            itemsPerPageSelect={{
              label: "Itens por página",
              values: [5, 10, 20, 50, 100],
              external: true
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
              email: "E-mail",
              bankData: "Dados bancários",
              balance: "Saldo",
              payment: "Pagar",
              active: "Ativo?"
            }}
            scopedSlots={{
              imageUrl: (item: any) => (
                <td className="m-auto bs-vertical-align-center" style={{width: 70}}>
                  <CImg src={item?.avatarUrl ? item.avatarUrl : noImage}
                        style={{width: 30, height: 30, borderRadius: 15}}/>
                </td>
              ),
              name: (item: any) =>
                <td>
                  <div>{item?.name} {item?.surname}</div>
                  <div className="small text-muted">{item?.cpf}</div>
                </td>,
              email: (item: any) => <td className="m-auto bs-vertical-align-center">{item?.email}</td>,
              bankData: (item: any) => <td>
                {item?.pixEmail && <div className="small text-muted">PIX: {item?.pixEmail}</div>}
                {item?.pixCpf && <div className="small text-muted">PIX: {item?.pixCpf}</div>}
                {item?.pixPhoneNumber && <div className="small text-muted">PIX: {item?.pixPhoneNumber}</div>}
              </td>,
              balance: (item: any) =>
                <td className="py-2 bs-vertical-align-center">
                  <div>
                    {item?.professionalBalance ? item?.professionalBalance.toLocaleString("pt-br", {
                      style: "currency",
                      currency: "BRL"
                    }) : "-"}
                  </div>
                  {/*<div className="small text-muted">01/10/2021 a 07/10/2021</div>*/}
                </td>,
              payment: (item: any) => (
                <td className="py-2 bs-vertical-align-center" style={{width: 120}}>
                  <div style={{flexDirection: "row"}}>
                    <CButton
                      block
                      color={item?.professionalBalance <= 0 ? "secondary" : "danger"}
                      className="btn btn-sm w-75"
                      onClick={() => {
                        setSelectedId(item.id);
                        setShowModal(true);
                      }}
                      disabled={item?.professionalBalance <= 0}
                    > {item?.professionalBalance <= 0 ? "Pago" : "Pagar"}</CButton>
                  </div>
                </td>
              ),
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
            }}
          />
        </CCardBody>
      </CCard>
      <ConfirmationModal
        showModal={showModal}
        setShowModal={setShowModal}
        onConfirm={doPayment}
        message={"o profissional terá o saldo ZERADO"}
        confirmButton={"Confirmar"}/>
    </>
  );
};
export default ProfessionalsView;
