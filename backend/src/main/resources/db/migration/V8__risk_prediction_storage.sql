-- Luu giai thich SHAP duoi dang chuoi JSON (VARCHAR) de Hibernate validate khop kieu String
ALTER TABLE risk_predictions MODIFY shap_json VARCHAR(6000) NULL;
