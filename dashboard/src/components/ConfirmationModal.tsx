import React from "react";
import {CButton, CModal, CModalBody, CModalFooter, CModalHeader, CModalTitle} from "@coreui/react";


interface Props {
  showModal: boolean;
  setShowModal: any;
  onConfirm: any;
  notes?: string;
  message?: string;
  confirmButton?: "Excluir" | "Confirmar"
}

const ConfirmationModal = (props: Props) => {

  function onConfirm() {
    props.onConfirm();
    props.setShowModal(false);
  }

  return (
    <>
      <CModal
        show={props.showModal}
        size={"sm"}
        onClose={() =>
          props.setShowModal(false)
        }>
        <CModalHeader closeButton>
          <CModalTitle>Atenção</CModalTitle>
        </CModalHeader>
        <CModalBody>
          Após a confirmação <b
          style={{color: "darkred"}}>{props?.message ? props?.message : "o item será permanentemente excluído"}</b>. Deseja continuar?
          {props.notes && <p style={{fontSize: 12, fontStyle: "italic", marginTop: 10}}>OBS: {props.notes}</p>}
        </CModalBody>
        <CModalFooter>
          <CButton
            color="secondary"
            onClick={() => props.setShowModal(false)}
          >
            Cancelar
          </CButton>
          <CButton
            color={props.confirmButton === "Excluir" ? "danger" : "success"}
            onClick={onConfirm}
          > {props.confirmButton}
          </CButton>
        </CModalFooter>
      </CModal>
    </>
  );
};

export default ConfirmationModal;
