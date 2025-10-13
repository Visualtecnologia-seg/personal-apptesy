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
  CImg,
  CPagination,
  CRow, CSpinner,
  CSwitch,
} from "@coreui/react";

import {ProductModel} from "../../model/ProductModel";
import {removeProduct, getProducts, postProduct} from "../../service/ProductService";
import {ParamsModel} from "../../model/ParamsModel";
import {useHistory} from "react-router-dom";
import ProductForm from "./ProductForm";
import {IoReload} from '../../react-icons';
import {useNotification} from "../../context/NotificationContext";
import ConfirmationModal from "../../components/ConfirmationModal";

const ProductView = () => {
  /* Default */
  const history = useHistory();
  const [loading, setLoading] = useState<boolean>(true);
  const [firstLoading, setFirstLoading] = useState<boolean>(true);
  /* Notification */
  const {setShowNotification, setNotification} = useNotification();
  /* Table */
  const [params, setParams] = useState<ParamsModel>({size: 10, page: 0, sort: "id,desc"});
  const [totalPages, setTotalPages] = useState<number>(0);
  const [products, setProducts] = useState<ProductModel[]>([]);
  /* Edit item */
  const [category, setCategory] = useState<ProductModel>({} as ProductModel);
  const [showModal, setShowModal] = useState(false);
  const [showConfirmationModal, setShowConfirmationModal] = useState(false);


  useEffect(() => {
    doGetProducts();
  }, [params]);

  const doGetProducts = () => {
    setLoading(true);
    getProducts().then(res => {
      if (res.error) {
        onError(res);
      } else {
        setTotalPages(res?.data?.totalPages);
        setProducts(res?.data?.content);
        setTimeout(() => setFirstLoading(false), 500);
        setTimeout(() => setLoading(false), 1000);
      }
    });
  };

  function onActiveAndInactive(category: ProductModel) {
    setLoading(true);
    category.active = !category.active;
    postProduct(category).then(res => {
      setShowNotification(false); // Workaround para garantir a mudança no state
      if (res.error) {
        onError(res);
      } else {
        setNotification({type: "success", notification: ["Salvo com sucesso"]});
        setShowNotification(true);
        doGetProducts();
      }
    });
  }

  function doDeleteCategory() {
    setLoading(true);
    removeProduct(category.id).then(res => {
      setShowNotification(false); // Workaround para garantir a mudança no state
      if (res.error) {
        onError(res);
      } else {
        setShowNotification(true);
        setNotification({type: "success", notification: ["Deletado com sucesso"]});
        doGetProducts();
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
    setCategory(item);
    setShowModal(true);
  };

  function openConfirmModal(item) {
    setCategory(item);
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
            > Produtos
            </div>
            <IoReload size={22} color={"#45a164"} style={{marginRight: 16}} onClick={() => {
              setLoading(true);
              doGetProducts();
            }}/>
            <CButton
              block
              color="primary"
              className="btn btn-sm"
              style={{width: 120, marginRight: 16}}
              onClick={() => openModal({id: null, title: "", urlImage: ""})}
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
            items={products}
            fields={["urlImage", "name", "about", "active", "actions"]}
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
              urlImage: "Foto",
              name: "Produto",
              about: "Sobre",
              active: "Publicada?",
              actions: " ",
            }}
            scopedSlots={{
              urlImage: (item: any) => (
                <td className="m-auto bs-vertical-align-center" style={{width: 100}}>
                  <CImg src={item.urlImage} style={{width: 70, height: 50, borderRadius: 8}}/>
                </td>
              ),
              name: (item: any) => <td
                className="m-auto bs-vertical-align-center">{item?.name}</td>,
              about: (item: any) => <td
                className="m-auto bs-vertical-align-center">{item?.about}</td>,
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
                    <CButton
                      block
                      color="primary"
                      className="btn btn-sm"
                      onClick={() => {
                        setCategory(item)
                        setShowModal(true);
                      }}
                    > Editar
                    </CButton>
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
      <ProductForm
        showModal={showModal}
        setShowModal={setShowModal}
        category={category}
        reload={() => doGetProducts()}/>
      <ConfirmationModal
        showModal={showConfirmationModal}
        setShowModal={setShowConfirmationModal}
        notes={"Para efetuar a exclusão, a categoria não poderá conter artigos relacionados."}
        onConfirm={() => doDeleteCategory()}/>
    </>
  );
};

export default ProductView;
