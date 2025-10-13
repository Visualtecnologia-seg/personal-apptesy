import React, {Component} from "react";
import {HashRouter, Route, Switch} from "react-router-dom";
import "./scss/style.scss";

const loading = (
  <div className="pt-3 text-center">
    <div className="sk-spinner sk-spinner-pulse"/>
  </div>
);

const TheLayout = React.lazy(() => import("./containers/TheLayout"));
const Login = React.lazy(() => import("./views/auth/AuthScreen"));

// TODO Melhor local para colocar a verifcação de status offline
class App extends Component {
  render() {
    return (
      <HashRouter>
        <React.Suspense fallback={loading}>
            <Switch>
              <Route exact path="/login" component={Login}/>
              <Route path="/" component={TheLayout}/>
            </Switch>
        </React.Suspense>
      </HashRouter>
    );
  }
}

export default App;
