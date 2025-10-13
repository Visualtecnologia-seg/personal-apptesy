import React, {ChangeEvent, useState} from "react";
import {
  CButton,
  CCol,
  CFormGroup,
  CInput,
  CModal,
  CModalBody,
  CModalFooter,
  CModalHeader,
  CModalTitle,
  CRow,
} from "@coreui/react";
import * as yup from "yup";
import {useNotification} from "../context/NotificationContext";
import {NotificationModel} from "../model/NotificationModel";
import {putPassword} from "../service/AuthService";


interface Props {
  showModal: boolean;
  setShowModal: (show) => void;
}

interface newPassword {
  newPassword?: string;
  confirmPassword?: string;
}

const ChangeUserPasswordForm = (props: Props) => {
  /* Notification */
  const {setShowNotification, setNotification} = useNotification();
  /* Credentials */
  const [password, setPassword] = useState<newPassword>();

  async function onSave() {
    setShowNotification(false);
    const validation: NotificationModel = {type: "error", notification: []};
    const userSchema = yup.object().shape({
      newPassword: yup
        .string()
        .min(3, () => validation.notification.push("A SENHA deve ter no mínimo 4 caracteres"))
        .required(() => (validation.notification.push("Campo SENHA obrigatório"))),
      confirmPassword: yup
        .string()
        .oneOf(
          [yup.ref("newPassword")],
          () => (validation.notification.push("As senhas não conferem")),
        )
        .required(() => (validation.notification.push("Campo CONFIRMAÇÃO obrigatório"))),
    });
    await userSchema.isValid(password);

    if (validation?.notification.length === 0) {
      putPassword(password).then(res => {
        if (res.error) {
          setNotification(res.error);
          setShowNotification(true);
        } else {
          props.setShowModal(false);
          setShowNotification(true);
        }
      });
    } else {
      setNotification(validation);
      setShowNotification(true);
    }
  }

  return (
    <>
      <CModal
        show={props.showModal}
        onClose={() =>
          props.setShowModal(false)
        }>
        <CModalHeader closeButton>
          <CModalTitle>Alteração de senha</CModalTitle>
        </CModalHeader>
        <CModalBody>
          <CRow>
            <CCol className="input-new-password">
              <CFormGroup>
                <CInput
                  id="newPass"
                  placeholder="Nova senha"
                  type="password"
                  onChange={(event: ChangeEvent<HTMLInputElement>) => {
                    setPassword({...password, newPassword: event.target.value});
                  }}
                />
              </CFormGroup>
            </CCol>
          </CRow>
          <CRow>
            <CCol className="input-new-password">
              <CFormGroup>
                <CInput
                  id="confirmNewPass"
                  placeholder="Confirmação de senha"
                  className="inputCheck"
                  type="password"
                  onChange={(event: ChangeEvent<HTMLInputElement>) => {
                    setPassword({...password, confirmPassword: event.target.value});
                  }}
                />
              </CFormGroup>
            </CCol>
          </CRow>
        </CModalBody>
        <CModalFooter>
          <CButton
            color="secondary"
            onClick={() => props.setShowModal(false)}
          >
            Cancelar
          </CButton>
          <CButton
            color="success"
            onClick={() => onSave()}
          > Salvar
          </CButton>
        </CModalFooter>

      </CModal>
    </>
  );
};

export default ChangeUserPasswordForm;
