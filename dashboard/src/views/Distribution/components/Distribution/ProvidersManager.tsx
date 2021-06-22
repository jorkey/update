import React, {useState} from 'react';
import clsx from 'clsx';
import { makeStyles } from '@material-ui/styles';
import {
  Card,
  CardHeader,
  CardContent,
  Button,
  Divider, Box,
} from '@material-ui/core';
import AddIcon from '@material-ui/icons/Add';
import GridTable from "../../../../common/components/gridTable/GridTable";
import {
  useAddProviderMutation, useChangeProviderMutation,
  useProvidersInfoQuery, useRemoveProviderMutation,
} from "../../../../generated/graphql";
import ConfirmDialog from "../../../../common/ConfirmDialog";
import {GridTableColumnParams} from "../../../../common/components/gridTable/GridTableRow";
import DeleteIcon from "@material-ui/icons/Delete";
import Alert from "@material-ui/lab/Alert";

const useStyles = makeStyles((theme:any) => ({
  root: {},
  content: {
    padding: 0
  },
  inner: {
    minWidth: 800
  },
  ProvidersTable: {
    marginTop: 20
  },
  distributionColumn: {
    padding: '4px',
    paddingLeft: '16px',
    width: '400px'
  },
  urlColumn: {
    padding: '4px',
    paddingLeft: '16px',
    width: '400px'
  },
  uploadStateInterval: {
    padding: '4px',
    paddingLeft: '16px'
  },
  controls: {
    display: 'flex',
    justifyContent: 'flex-end'
  },
  control: {
    marginLeft: '50px',
    textTransform: 'none'
  },
  alert: {
    marginTop: 25
  }
}));

const ProvidersManager = () => {
  const classes = useStyles()

  const [error, setError] = useState<string>()

  const { data: providers, refetch: refetchProviders } = useProvidersInfoQuery({
    fetchPolicy: 'no-cache',
    onError(err) { setError('Query providers info error ' + err.message) },
    onCompleted() { setError(undefined) }
  })

  const [ addProvider ] = useAddProviderMutation({
    fetchPolicy: 'no-cache',
    onError(err) { setError('Add provider error ' + err.message) },
    onCompleted() { setError(undefined) }
  })

  const [ changeProvider ] = useChangeProviderMutation({
    fetchPolicy: 'no-cache',
    onError(err) { setError('Change provider error ' + err.message) },
    onCompleted() { setError(undefined) }
  })

  const [ removeProvider ] = useRemoveProviderMutation({
    fetchPolicy: 'no-cache',
    onError(err) { setError('Remove provider error ' + err.message) },
    onCompleted() { setError(undefined) }
  })

  const [ addNewRow, setAddNewRow ] = useState(false)
  const [ deleteConfirm, setDeleteConfirm ] = useState<string>()

  const columns: Array<GridTableColumnParams> = [
    {
      name: 'distribution',
      headerName: 'Distribution',
      className: classes.distributionColumn,
      validate: (value, rowNum) => {
        return !!value &&
          !rows.find((row, index) => {
            return index != rowNum && row.get('distribution') == value
          })
      }
    },
    {
      name: 'url',
      headerName: 'URL',
      className: classes.urlColumn,
      editable: true,
      validate: (value) => {
        try {
          if (!value) {
            return false
          }
          new URL(value as string)
          return true
        } catch {
          return false
        }
      }
    },
    {
      name: 'uploadStateInterval',
      headerName: 'Upload State Interval (sec)',
      className: classes.uploadStateInterval,
      type: 'number',
      editable: true,
      validate: (value) => {
        try {
          if (!value) {
            return false
          }
          return !isNaN(value as number)
        } catch {
          return false
        }
      }
    }
  ]

  const rows = new Array<Map<string, string>>()
  providers?.providersInfo.forEach(provider => { rows.push(new Map([
    ['distribution', provider.distribution],
    ['url', provider.url],
    ['uploadStateInterval', provider.uploadStateIntervalSec?provider.uploadStateIntervalSec.toString():'']
  ])) })

  return (
    <Card
      className={clsx(classes.root)}
    >
      <CardHeader
        action={
          <Box
            className={classes.controls}
          >
            <Button
              color="primary"
              variant="contained"
              className={classes.control}
              startIcon={<AddIcon/>}
              onClick={() => setAddNewRow(true) }
            >
              Add New Provider
            </Button>
          </Box>
        }
        title={'Providers'}
      />
      <Divider/>
      <CardContent className={classes.content}>
        <div className={classes.inner}>
          <GridTable
            className={classes.ProvidersTable}
            columns={columns}
            rows={rows}
            editable={true}
            actions={[<DeleteIcon/>]}
            addNewRow={addNewRow}
            onRowAddCancelled={ () => setAddNewRow(false) }
            onRowAdded={(row) => {
              setAddNewRow(false)
              addProvider({ variables: {
                distribution: String(row.get('distribution')!),
                url: String(row.get('url')!),
                uploadStateIntervalSec: Number(row.get('uploadStateInterval')!)
              }}).then(() => refetchProviders())
            }}
            onRowChanged={(row, values, oldValues) => {
              setAddNewRow(false)
              return changeProvider({ variables: {
                  distribution: String(values.get('distribution')!),
                  url: String(values.get('url')!),
                  uploadStateIntervalSec: Number(values.get('uploadStateInterval')!)
                }}).then(() => refetchProviders().then(() => {}))}}
            onAction={(action, row, values) =>
              setDeleteConfirm(String(values.get('distribution')!))
            }
          />
          { deleteConfirm ? (
            <ConfirmDialog
              message={`Do you want to delete Provider '${deleteConfirm}'?`}
              open={true}
              close={() => { setDeleteConfirm(undefined) }}
              onConfirm={() => removeProvider({ variables: {
                  distribution: deleteConfirm }}).then(() => refetchProviders()) }
            />) : null }
          {error && <Alert className={classes.alert} severity="error">{error}</Alert>}
        </div>
      </CardContent>
    </Card>
  );
}

export default ProvidersManager