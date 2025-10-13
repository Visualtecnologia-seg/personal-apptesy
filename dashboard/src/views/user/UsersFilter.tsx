import React, {ChangeEvent, useEffect, useState} from "react";
import {
  CButton,
  CCard,
  CCardBody,
  CCardHeader,
  CCol,
  CCollapse,
  CFormGroup,
  CInput,
  CRow,
  CSelect,
} from "@coreui/react";
import {ChevronDown, Search} from "react-feather";
import {UserModel} from "../../model/UserModel";

interface Props {
  filter: (filter) => void;
}

const UsersFilter = (props: any) => {
  const [collapse, setCollapse] = useState(true);
  const [filter, setFilter] = useState<UserModel>({} as UserModel);
  const [institutions, setInstitutions] = useState<any>([]);
  const emptyFilter = {name: "", age: "", institution: "", grade: ""};

  useEffect(() => {

  }, []);

  const toggle = (param: any) => {
    setCollapse(!collapse);
    param.preventDefault();
  };

  return (
    <CCard accentColor="primary">
      <CCardHeader className="table-header filter-header">
        <CRow className="align-items-center" onClick={toggle}>
          <Search height="18" className="mr-2 bd-highlight"/>
          Filtro
          <ChevronDown className="ml-auto"/>
        </CRow>
      </CCardHeader>
      <CCollapse show={collapse}>
        {/*<CCardBody>*/}
        {/*  <CFormGroup row className="my-0">*/}
        {/*    <CCol xs="12" sm="3">*/}
        {/*      <CFormGroup>*/}
        {/*        <CInput*/}
        {/*          placeholder="Nome"*/}
        {/*          value={filter?.name}*/}
        {/*          onChange={(event: ChangeEvent<HTMLInputElement>) => {*/}
        {/*            setFilter({...filter, name: event.target.value});*/}
        {/*          }}*/}
        {/*        />*/}
        {/*      </CFormGroup>*/}
        {/*    </CCol>*/}
        {/*    <CCol xs="12" sm="3">*/}
        {/*      <CFormGroup>*/}
        {/*        <CInput*/}
        {/*          placeholder="Idade"*/}
        {/*          value={filter?.age}*/}
        {/*          onChange={(event: ChangeEvent<HTMLInputElement>) => {*/}
        {/*            setFilter({...filter, age: event.target.value});*/}
        {/*          }}*/}
        {/*          type="number"*/}
        {/*        />*/}
        {/*      </CFormGroup>*/}
        {/*    </CCol>*/}
        {/*    <CCol xs="12" sm="3">*/}
        {/*      <CFormGroup>*/}
        {/*        <CSelect*/}
        {/*          value={filter?.institution}*/}
        {/*          onChange={(event: ChangeEvent<HTMLInputElement>) => {*/}
        {/*            institutions?.forEach((value) => {*/}
        {/*              if (event.target.value !== "0") {*/}
        {/*                setFilter({...filter, institution: event.target.value});*/}
        {/*              }*/}
        {/*            });*/}
        {/*          }}>*/}
        {/*          <option value="0">Instituição</option>*/}
        {/*          {institutions?.map((institution) => {*/}
        {/*            return (*/}
        {/*              <option key={institution?.id} value={institution?.id}>*/}
        {/*                {institution?.name}*/}
        {/*              </option>*/}
        {/*            );*/}
        {/*          })}*/}
        {/*        </CSelect>*/}
        {/*      </CFormGroup>*/}
        {/*    </CCol>*/}
        {/*    <CCol xs="12" sm="3">*/}
        {/*      <CFormGroup>*/}
        {/*        <CSelect*/}
        {/*          value={filter?.grade}*/}
        {/*          onChange={(event: ChangeEvent<HTMLInputElement>) => {*/}
        {/*            institutions?.forEach((value) => {*/}
        {/*              if (event.target.value !== "0") {*/}
        {/*                setFilter({...filter, grade: event.target.value});*/}
        {/*              }*/}
        {/*            });*/}
        {/*          }}>*/}
        {/*          <option value="0">Série</option>*/}
        {/*          <option value="4ª Série">4ª Série</option>*/}
        {/*          <option value="5ª Série">5ª Série</option>*/}
        {/*          <option value="6ª Série">6ª Série</option>*/}
        {/*          <option value="7ª Série">7ª Série</option>*/}
        {/*          <option value="8ª Série">8ª Série</option>*/}
        {/*          <option value="9ª Série">9ª Série</option>*/}
        {/*          <option value="Ensino Médio">Ensino Médio</option>*/}
        {/*          <option value="Outra">Outra</option>*/}
        {/*        </CSelect>*/}
        {/*      </CFormGroup>*/}
        {/*    </CCol>*/}
        {/*  </CFormGroup>*/}
        {/*  <CRow className="justify-content-center align-items-center">*/}
        {/*    <div*/}
        {/*      className="flex-grow-1"*/}
        {/*      style={{marginLeft: 10, fontWeight: "bolder"}}*/}
        {/*    />*/}
        {/*    <CButton*/}
        {/*      block*/}
        {/*      color="danger"*/}
        {/*      className="btn btn-sm "*/}
        {/*      style={{width: 100, marginRight: 5}}*/}
        {/*      onClick={() => {*/}
        {/*        setFilter(emptyFilter);*/}
        {/*        props.filter(emptyFilter);*/}
        {/*      }}*/}
        {/*    >*/}
        {/*      Limpar Filtros*/}
        {/*    </CButton>*/}
        {/*    <div*/}
        {/*      style={{fontWeight: "bolder"}}*/}
        {/*    />*/}
        {/*    <CButton*/}
        {/*      block*/}
        {/*      color="success"*/}
        {/*      className="btn btn-sm "*/}
        {/*      style={{width: 100, marginRight: 16}}*/}
        {/*      onClick={() => {*/}
        {/*        props.filter(filter);*/}
        {/*      }}*/}
        {/*    >*/}
        {/*      Filtrar*/}
        {/*    </CButton>*/}
        {/*  </CRow>*/}
        {/*</CCardBody>*/}
      </CCollapse>
    </CCard>
  );
};

export default UsersFilter;
