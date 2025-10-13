import React, {ChangeEvent, useEffect, useState} from "react";
import {
  CButton,
  CCard,
  CCardBody,
  CCardGroup,
  CCol,
  CContainer,
  CForm,
  CInput,
  CInputGroup,
  CInputGroupPrepend,
  CInputGroupText,
  CRow,
} from "@coreui/react";
import {Lock, User} from "react-feather";
import {CredentialsModel} from "../../model/CredentialsModel";
import {authenticate} from "../../service/AuthService";
import {useNotification} from "../../context/NotificationContext";

const AuthScreen = () => {
  /* Notification */
  const {setShowNotification, setNotification, showNotification} = useNotification();
  const [isOnline, setOnline] = useState(window.navigator.onLine);
  /* Credentials */
  const [credentials, setCredentials] = useState({} as CredentialsModel);

  // TODO Remover o código duplicado
  useEffect(() => {
    window.addEventListener("offline",
      () => {
        if (showNotification) {
          setShowNotification(false); // Workaround para garantir a mudança no state
        }
        setNotification({type: "offline", notification: ["Falha na conexão com a internet!"]});
        setOnline(window.navigator.onLine);
        setShowNotification(isOnline);
      },
    );
    window.addEventListener("online",
      () => {
        if (showNotification) {
          setShowNotification(false); // Workaround para garantir a mudança no state
        }
        setNotification({type: "offline", notification: ["Falha na conexão com a internet!"]});
        setOnline(window.navigator.onLine);
        setShowNotification(isOnline);
      },
    );
  });

  function handleData(obj: CredentialsModel) {
    setCredentials({...credentials, ...obj});
  }

  const auth = () => {
    setShowNotification(false);
    authenticate(credentials).then(res => {
      if (res.error) {
        setShowNotification(true);
        setNotification(res.error);
      } else {
        return window.location.href = "/#/article";
      }
    });
  };

  return (
    <div className="c-app c-default-layout flex-row align-items-center"
         style={{background: `linear-gradient(to bottom,  #000000 0%, #00509d 100%)`}}>
      <CContainer>
        <CRow className="justify-content-center">
          <CCol md="4">
            <CCardGroup>
              <CCard className="p-4" style={{backgroundColor: "rgba(255,255,255,0.9", borderRadius: 15}}>
                <CCardBody>
                  <p className="bs-login-title" style={{textAlign: "center"}}>Personal Fighters</p>
                  <CForm>
                    <CInputGroup className="mb-3">
                      <CInputGroupPrepend>
                        <CInputGroupText>
                          <User size="19"/>
                        </CInputGroupText>
                      </CInputGroupPrepend>
                      <CInput
                        type="text"
                        placeholder="Usuário"
                        autoComplete="username"
                        value={credentials?.username}
                        onChange={(event: ChangeEvent<HTMLInputElement>) => {
                          handleData({
                            ...credentials,
                            username: event.target.value,
                          });
                        }}
                      />
                    </CInputGroup>
                    <CInputGroup className="mb-4">
                      <CInputGroupPrepend>
                        <CInputGroupText>
                          <Lock size="19"/>
                        </CInputGroupText>
                      </CInputGroupPrepend>
                      <CInput
                        type="password"
                        placeholder="Senha"
                        autoComplete="current-password"
                        value={credentials?.password}
                        onChange={(event: ChangeEvent<HTMLInputElement>) => {
                          handleData({
                            ...credentials,
                            password: event.target.value,
                          });
                        }}
                      />
                    </CInputGroup>
                    <CRow className="justify-content-end" style={{marginRight: 0}}>
                      <CButton
                        color="primary"
                        onClick={() => auth()}
                      > Entrar
                      </CButton>
                    </CRow>
                  </CForm>
                </CCardBody>
              </CCard>
            </CCardGroup>
          </CCol>
        </CRow>
      </CContainer>
    </div>
  );
};

export default AuthScreen;
