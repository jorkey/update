import React, {useRef, useState} from 'react';
import clsx from 'clsx';
import PropTypes from 'prop-types';
import { makeStyles } from '@material-ui/styles';
import { Utils } from '../../../../common';
import {
  Card,
  CardHeader,
  CardContent,
  Button,
  Divider,
  InputLabel,
  Select
} from '@material-ui/core';
import RefreshIcon from "@material-ui/icons/Refresh";
import FormGroup from "@material-ui/core/FormGroup";
import FormControlLabel from "@material-ui/core/FormControlLabel";
import Checkbox from "@material-ui/core/Checkbox";
import {VersionsTable} from "./VersionsTable";

const useStyles = makeStyles(theme => ({
  root: {},
  content: {
    padding: 0
  },
  inner: {
    minWidth: 800
  },
  statusContainer: {
    display: 'flex',
    alignItems: 'center'
  },
  status: {
    marginRight: theme.spacing(1)
  },
  actions: {
    justifyContent: 'flex-end'
  },
  formControlLabel: {
    paddingLeft: '10px'
  },
  clientSelect: {
    width: '100px'
  },
  onlyAlerts: {
    paddingRight: '2px'
  }
}));

const Versions = props => {
  const { className, ...rest } = props;
  const classes = useStyles();

  const [client, setClient] = useState()
  const [clients, setClients] = useState([])
  const [desiredVersions, setDesiredVersions] = useState([])
  const [clientVersions, setClientVersions] = useState(new Map())
  const [instanceVersions, setInstanceVersions] = useState(new Map())
  const [onlyAlerts, setOnlyAlerts] = useState(false)

  const distributionClient = false // TODO localStorage.getItem('distribution').client

  React.useEffect(() => {
    if (!distributionClient) {
      Utils.getClients().then(clients => {
        setClients(clients)
        if (clients.length) {
          setClient(clients[0])
        }
      })
    }
  }, [])

  React.useEffect(() => {
    setDesiredVersions([])
    setClientVersions(new Map())
    setInstanceVersions(new Map())
    getClientVersions(client)
  }, [client]);

  const getClientVersions = (client) => {
    if (client) {
      Utils.getDesiredVersions(client).then(versions => {
        setDesiredVersions(Object.entries(versions))
        if (client == "distribution") {
          setClientVersions(new Map(Object.entries(versions)))
        }
      })
      Utils.getInstanceVersions(client).then(versions => {
        setInstanceVersions(new Map(Object.entries(versions)))
      })
      if (client != "distribution") {
        Utils.getInstalledDesiredVersions(client).then(versions => {
          setClientVersions(new Map(Object.entries(versions)))
        })
      }
    }
  }

  return (
    <Card
      {...rest}
      className={clsx(classes.root, className)}
    >
      <CardHeader
        action={
          <FormGroup row>
            <FormControlLabel
              label="Client"
              className={classes.formControlLabel}
              control={ <Select
                    title="Select client"
                    className={classes.clientSelect}
                    native
                    value={client}
                    onChange={(event) => {
                      setClient(event.target.value);
                    }}
                  >
                  { clients.map( client => <option key={client}>{client}</option> ) }
                </Select> }
            />
            <FormControlLabel
              label="Only Alerts"
              className={classes.formControlLabel}
              control={ <Checkbox
                className={classes.onlyAlerts}
                checked={onlyAlerts}
                onChange={event => setOnlyAlerts(event.target.checked)}
              /> }
            />
            <FormControlLabel
              className={classes.formControlLabel}
              control={ <Button title="Refresh" onClick={() => getClientVersions(client)}>
                <RefreshIcon/>
                <InputLabel>{new Date().getHours().toLocaleString(undefined, {minimumIntegerDigits: 2}) +
                  ":" + new Date().getMinutes().toLocaleString(undefined, {minimumIntegerDigits: 2}) +
                  ":" + new Date().getSeconds().toLocaleString(undefined, {minimumIntegerDigits: 2})}</InputLabel>
              </Button> }
            />
          </FormGroup>
        }
        title={onlyAlerts ? "Version Alerts" : "Versions" }
      />
      <Divider />
      <CardContent className={classes.content}>
        <div className={classes.inner}>
          <VersionsTable client={client} distributionclient={distributionClient}
                         desiredVersions={desiredVersions} clientVersions={clientVersions} instanceVersions={instanceVersions}
                         onlyAlerts={onlyAlerts}/>
        </div>
      </CardContent>
    </Card>
  );
};

Versions.propTypes = {
  className: PropTypes.string
};

export default Versions;
