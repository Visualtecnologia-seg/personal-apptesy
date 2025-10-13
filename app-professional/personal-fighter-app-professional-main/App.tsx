import { LogBox } from "react-native";
import App from "./src";

LogBox.ignoreLogs(["VirtualizedLists should never be nested"]);
LogBox.ignoreAllLogs();//Ignore all log notifications

export default App;
