import React, {Suspense, useEffect, useState} from "react";
import {Redirect, Route, Switch} from "react-router-dom";
import {CContainer, CFade} from "@coreui/react";
import {getStatus} from "../service/NetworkService";
import routes from "../routes";
import {useNotification} from "../context/NotificationContext";

const loading = (
  <div className="pt-3 text-center">
    <div className="sk-spinner sk-spinner-pulse"/>
  </div>
);

const TheContent = () => {
  const [isOnline, setOnline] = useState(window.navigator.onLine);
  const {setShowNotification, setNotification, showNotification} = useNotification();

  useEffect(() => {
    const checkConnection = setInterval(
      async () => {
        const response = await getStatus();
        if (response === "offline") {
         // setNotification({type: "offline", notification: ["Falha na conexão com a internet!"]});
         // setShowNotification(true);
        } else {
         // setShowNotification(false);
        }
      }, 1000 * 45);

    return () => clearInterval(checkConnection);
  });

  return (
    <main className="c-main">
      {/*{showNotification && <div className="modal-backdrop fade show"/>}*/}
      <CContainer fluid>
        <Suspense fallback={loading}>
          <Switch>
            {routes.map((route, idx) => {
              return route.component && (
                <Route
                  key={idx}
                  path={route.path}
                  children={() => (
                    <CFade>
                      <route.component/>
                    </CFade>
                  )}/>
              );
            })}
            <Redirect from="/" to="/products"/>
          </Switch>
        </Suspense>
      </CContainer>
    </main>
  );
};

export default React.memo(TheContent);
