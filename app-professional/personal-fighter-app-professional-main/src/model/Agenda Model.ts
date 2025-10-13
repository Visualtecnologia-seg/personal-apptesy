import {ProfessionalModel} from "./ProfessionalModel";

export type AgendaModel = {
  id?: string;
  dayOfWeek?: string;
  status?: string;
  schedule?: string;
  profession?: ProfessionalModel;
};