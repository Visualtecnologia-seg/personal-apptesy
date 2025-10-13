import React, {ChangeEvent, useEffect, useState} from "react";
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
import {CKEditor} from "@ckeditor/ckeditor5-react";
import ClassicEditor from "@ckeditor/ckeditor5-build-classic";
import * as yup from "yup";
import {NotificationModel} from "../../model/NotificationModel";
import {useNotification} from "../../context/NotificationContext";
import {InfoModel} from "../../model/InfoModel";
import S3Upload from "../../utils/S3Upload";
import {postInfo} from "../../service/InfoService";

interface Props {
  showModal: boolean;
  setShowModal: any;
  info: InfoModel;
  reload?: any;
}

const InfoForm = (props: Props) => {
  /* Notification */
  const {setShowNotification, setNotification} = useNotification();
  /* Form */
  const [info, setInfo] = useState<InfoModel>(props.info);

  useEffect(() => {
    setInfo(props.info);
  }, [props.info]);

  /* CKEditor */
  const custom_config = {
    extraPlugins: [UploadAdapterPlugin],
    toolbar: {
      items: [
        "heading",
        "|",
        "bold",
        "italic",
        "link",
        "bulletedList",
        "numberedList",
        "|",
        "imageUpload",
        "undo",
        "redo",
      ],
    },
  };

  function UploadAdapterPlugin(editor) {
    editor.plugins.get("FileRepository").createUploadAdapter = (loader) => {
      return new S3Upload(loader);
    };
  }

  async function onSave() {
    setShowNotification(false);
    const validation: NotificationModel = {type: "error", notification: []};
    const userSchema = yup.object().shape({
      title: yup
        .string()
        .required(() => (validation.notification.push("O campo TÍTULO é obrigatório"))),
      content: yup
        .string()
        .required(() => (validation.notification.push("O campo de CONTEÚDO é obrigatório"))),
    });
    await userSchema.isValid(info);
    if (validation?.notification.length === 0) {
      console.log("save -> props.info -> ", props.info);
      console.log("save -> info -> ", info);
      if (info.id) {
        info.active = false;
      }
      postInfo(info).then(res => {
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

  return (
    <>
      <CModal show={props.showModal} onClose={props.setShowModal} size={"lg"}>
        <CModalHeader closeButton>
          <CModalTitle>{info?.id ? "Novo" : "Editar"}</CModalTitle>
        </CModalHeader>
        <CModalBody>
          <CRow>
            <CCol xs="12" sm="12">
              <CFormGroup>
                <CInput
                  id="bank"
                  placeholder="Título do informativo"
                  value={info?.title}
                  onChange={(event: ChangeEvent<HTMLInputElement>) => {
                    setInfo({...info, title: event.target.value});
                  }}
                />
              </CFormGroup>
            </CCol>
            <CCol xs="12" sm="12">
              <CFormGroup>
                <CKEditor
                  editor={ClassicEditor}
                  data={info?.content}
                  config={custom_config}
                  onReady={editor => editor.editing.view.change(writer => {
                    writer.setStyle(
                      "height",
                      "340px",
                      editor.editing.view.document.getRoot(),
                    );
                  })}
                  onChange={(event, editor) => {
                    const content = editor.getData();
                    setInfo({...info, content: content});
                  }}
                />
              </CFormGroup>
            </CCol>
          </CRow>
        </CModalBody>
        <CModalFooter>
          <CButton color="secondary" onClick={() => {
            setInfo({} as InfoModel);
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

export default InfoForm;
