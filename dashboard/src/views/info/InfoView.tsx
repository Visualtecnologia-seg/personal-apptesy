import React, {useEffect, useState} from "react";
import {
  CButton,
  CCard,
  CCardBody,
  CCardHeader,
  CDataTable,
  CDropdown,
  CDropdownItem,
  CDropdownMenu,
  CDropdownToggle,
  CPagination,
  CRow,
  CSpinner,
  CSwitch,
} from "@coreui/react";
import {ParamsModel} from "../../model/ParamsModel";
import {useHistory} from "react-router-dom";
import InfoForm from "./InfoForm";
import {IoReload} from '../../react-icons';
import {useNotification} from "../../context/NotificationContext";
import {removeInfo, getInfos, postInfo} from "../../service/InfoService";
import {InfoModel} from "../../model/InfoModel";
import ConfirmationModal from "../../components/ConfirmationModal";

const InfoView = () => {
  /* Default */
  const history = useHistory();
  const [loading, setLoading] = useState<boolean>(true);
  const [firstLoading, setFirstLoading] = useState<boolean>(true);
  /* Notification */
  const {setShowNotification, setNotification} = useNotification();
  /* Table */
  const [params, setParams] = useState<ParamsModel>({size: 10, page: 0, sort: "id,desc"});
  const [totalPages, setTotalPages] = useState<number>(0);
  const [infos, setInfos] = useState<InfoModel[]>([]);
  /* Edit item */
  const [info, setInfo] = useState<InfoModel>({} as InfoModel);
  const [showEditModal, setShowEditModal] = useState(false);
  const [showConfirmationModal, setShowConfirmationModal] = useState(false);

  useEffect(() => {
    doGetInfos();
  }, [params]);

  const doGetInfos = () => {
    setLoading(true);
    getInfos(params).then(res => {
      if (res.error) {
        onError(res);
      } else {
        setTotalPages(res?.data?.totalPages);
        setInfos(res?.data?.content);
        setTimeout(() => setFirstLoading(false), 500);
        setTimeout(() => setLoading(false), 1000);
      }
    });
  };

  function onActiveAndInactive(info: InfoModel) {
    setLoading(true);
    info.active = !info.active;
    postInfo(info).then(res => {
      setShowNotification(false); // Workaround para garantir a mudança no state
      if (res.error) {
        onError(res);
      } else {
        setNotification({type: "success", notification: ["Salvo com sucesso"]});
        setShowNotification(true);
        doGetInfos();
      }
    });
  }

  function doDeleteInfo() {
    setLoading(true);
    removeInfo(info.id).then(res => {
      setShowNotification(false); // Workaround para garantir a mudança no state
      if (res.error) {
        onError(res);
      } else {
        setShowNotification(true);
        setNotification({type: "success", notification: ["Deletado com sucesso"]});
        doGetInfos();
      }
    });
  }

  function onError(res) {
    setShowNotification(false); // Workaround para garantir a mudança no state
    setShowNotification(true);
    setNotification(res.error);
    setTimeout(() => setLoading(false), 1000);
  }

  const openModal = (item: any) => {
    setInfo(item);
    setShowEditModal(true);
  };

  function openConfirmModal(item) {
    setInfo(item);
    setShowConfirmationModal(true);
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
      <CCard accentColor="primary">
        <CCardHeader className="table-header">
          <CRow className="justify-content-center align-items-center">
            <div
              className="flex-grow-1 bs-title"
              style={{marginLeft: 10}}
            > Informativos
            </div>
            <IoReload size={22} color={"#45a164"} style={{marginRight: 16}} onClick={() => {
              setLoading(true);
              doGetInfos();
            }}/>
            <CButton
              block
              color="primary"
              className="btn btn-sm"
              style={{width: 120, marginRight: 16}}
              onClick={() => openModal({id: undefined, title: "", content: ""})}
            > Novo
            </CButton>
          </CRow>
        </CCardHeader>
        <CCardBody>
          <CDataTable
            loading={loading}
            hover
            striped
            responsive
            size="md"
            items={infos}
            fields={["title", "active", "actions"]}
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
            noItemsViewSlot={
              <div className="text-center my-5">
                <h2>Sem conteúdo</h2>
              </div>
            }
            columnHeaderSlot={{
              title: "Título",
              active: "Publicado?",
              actions: " ",
            }}
            scopedSlots={{
              title: (item: any) => <td
                className="m-auto bs-vertical-align-center">{item.title}</td>,
              active: (item: any) => (
                <td className="py-2 bs-vertical-align-center" style={{width: 120}}>
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
              actions: (item: any) => (
                <td className="py-2 bs-vertical-align-center" style={{width: 120}}>
                  <div style={{flexDirection: "row"}}>
                    <CDropdown className="m-1">
                      <CDropdownToggle color="secondary">
                        Ações
                      </CDropdownToggle>
                      <CDropdownMenu placement={"bottom-end"}>
                        <CDropdownItem
                          onClick={() => openModal(item)}>
                          Editar
                        </CDropdownItem>
                        <CDropdownItem
                          disabled={item.active}
                          onClick={() => openConfirmModal(item)}>
                          Deletar
                        </CDropdownItem>
                      </CDropdownMenu>
                    </CDropdown>
                  </div>
                </td>
              ),
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
      {/* Modals */}
      {/* Workaround para forçar a renderização do CKEditor */}
      {showEditModal && <InfoForm
        showModal={showEditModal}
        setShowModal={setShowEditModal}
        info={info}
        reload={() => doGetInfos()}/>}
      <ConfirmationModal
        showModal={showConfirmationModal}
        setShowModal={setShowConfirmationModal}
        onConfirm={() => doDeleteInfo()}/>
    </>
  );
};

export default InfoView;
