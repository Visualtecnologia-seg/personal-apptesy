import React, {ChangeEvent, useEffect, useRef, useState} from "react";
import {
  CButton,
  CCol,
  CFormGroup,
  CImg,
  CInput,
  CModal,
  CModalBody,
  CModalFooter,
  CModalHeader,
  CModalTitle,
  CRow,
  CSpinner, CTextarea
} from "@coreui/react";
import {ProductModel} from "../../model/ProductModel";
import noImage from "../../assets/no-image.jpg";
import {postProduct} from "../../service/ProductService";
import * as yup from "yup";
import {NotificationModel} from "../../model/NotificationModel";
import {useNotification} from "../../context/NotificationContext";
import {imageUpload} from "../../service/S3UploadService";

interface Props {
  showModal: boolean;
  setShowModal: any;
  category: ProductModel;
  reload?: any;
}

const ProductForm = (props: Props) => {
  /* Notification */
  const {setShowNotification, setNotification} = useNotification();
  /* Form */
  const [category, setCategory] = useState<ProductModel>({});
  const uploadImageRef = useRef<any>(null);
  const [loadingImage, setLoadingImage] = useState<boolean>(false);

  useEffect(() => {
    setCategory(props.category);
  }, [props.category]);

  function handleData(obj: any) {
    setCategory({...category, ...obj});
  }

  async function onSave() {
    setShowNotification(false);
    const validation: NotificationModel = {type: "error", notification: []};
    const userSchema = yup.object().shape({
      title: yup
        .string()
        .required(() => (validation.notification.push("O campo TÍTULO é obrigatório"))),
      urlImage: yup
        .string()
        .nullable()
        .required(() => (validation.notification.push("O campo IMAGEM é obrigatório"))),
    });
    await userSchema.isValid(category);
    if (validation?.notification.length === 0) {
      if (category.id) {
        category.active = false;
      }
      postProduct(category).then(res => {
        if (res.error) {
          setNotification(res.error);
          setShowNotification(true);
        } else {
          props.setShowModal(false);
          props.reload();
          setNotification({type: "success", notification: ["Salvo com sucesso"]});
          setShowNotification(true);
        }
      });
    } else {
      setNotification(validation);
      setShowNotification(true);
    }
  }


  const fileUpload = async (event: React.ChangeEvent<HTMLInputElement>) => {
    setLoadingImage(true);
    const file = event.target.files;
    if (file !== null) {
      const response = await imageUpload(file);
      if (response === "Error") {
        setNotification({type: "error", notification: ["O arquivo deve ser menor do que 1MB."]});
        setShowNotification(true);
        setLoadingImage(false);
        return;
      } else {
        setCategory({...category, urlImage: response.data});
        setLoadingImage(false);
      }
    }
  };

  return (
    <>
      <CModal show={props.showModal} onClose={props.setShowModal}>
        <CModalHeader closeButton>
          <CModalTitle>{category?.id === null ? "Nova" : "Editar"}</CModalTitle>
        </CModalHeader>
        <CModalBody>
          <CRow>
            <CCol xs="12" sm="12">
              <CFormGroup>
                <CInput
                  id="bank"
                  placeholder="Nome da categoria"
                  value={category?.name}
                  onChange={(event: ChangeEvent<HTMLInputElement>) => {
                    handleData({name: event.target.value});
                  }}
                />
              </CFormGroup>
            </CCol>
            <CCol xs="12" sm="12">
              <CFormGroup>
                <CTextarea
                  id="bank"
                  placeholder="Sobre"
                  value={category?.about}
                  rows={5}
                  maxLength={254}
                  onChange={(event: ChangeEvent<HTMLInputElement>) => {
                    handleData({about: event.target.value});
                  }}
                />
              </CFormGroup>
            </CCol>
            <CCol xs="12" sm="12">
              <CFormGroup>
                <CTextarea
                  id="bank"
                  placeholder="Equipamentos"
                  value={category?.equipment}
                  rows={5}
                  maxLength={254}
                  onChange={(event: ChangeEvent<HTMLInputElement>) => {
                    handleData({equipment: event.target.value});
                  }}
                />
              </CFormGroup>
            </CCol>
            <CCol xs="12" sm="12">
              <CFormGroup>
                {loadingImage ?
                  <div className="d-flex justify-content-center align-items-end" style={{marginTop: 30}}>
                    <CSpinner color="primary" style={{width: "2rem", height: "2rem"}}/>
                  </div>
                  :
                  <>
                    <CImg
                      src={category?.urlImage ? category.urlImage : noImage}
                      style={{height: 190, borderRadius: 8}}
                      fluidGrow={true}
                    />
                    <CCol xs="3" sm="3" style={{marginTop: -40}}>
                      <CButton
                        block
                        color="secondary"
                        className="btn btn-sm"
                        onClick={() => uploadImageRef.current != undefined && uploadImageRef.current.click()}
                        style={{minWidth: 60}}
                      > Alterar
                      </CButton>
                    </CCol>
                    <input type="file" onChange={fileUpload} ref={uploadImageRef} accept="image/*"
                           style={{display: "none"}}/>
                  </>}
              </CFormGroup>
            </CCol>
          </CRow>
        </CModalBody>
        <CModalFooter>
          <CButton color="secondary" onClick={() => {
            props.setShowModal(false);
          }}>
            Cancelar
          </CButton>
          <CButton color="success" onClick={() => onSave()}>
            Salvar
          </CButton>
        </CModalFooter>
      </CModal>
    </>
  );
};

export default ProductForm;
