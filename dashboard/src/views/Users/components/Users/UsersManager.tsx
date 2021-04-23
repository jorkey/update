import React, {useState} from 'react';
import clsx from 'clsx';
import { makeStyles } from '@material-ui/styles';
import {
  Card,
  CardHeader,
  CardContent,
  Button,
  Divider, Box, Link,
} from '@material-ui/core';
import AddIcon from '@material-ui/icons/Add';
import UsersTable from './UsersTable';
import {RouteComponentProps, useRouteMatch} from "react-router-dom";

const useStyles = makeStyles((theme:any) => ({
  root: {},
  content: {
    padding: 0
  },
  inner: {
    minWidth: 800
  },
  controls: {
    display: 'flex',
    justifyContent: 'flex-end'
  },
  control: {
    marginLeft: '50px',
    textTransform: 'none'
  }
}));

interface UsersManagerRouteParams {
  type: string
}

const UsersManager: React.FC<RouteComponentProps<UsersManagerRouteParams>> = props => {
  const usersType = props.match.params.type

  const classes = useStyles()
  const routeMatch = useRouteMatch();

  return (
    <Card
      className={clsx(classes.root)}
    >
      <CardHeader
        action={
          <Box
            className={classes.controls}
          >
            <Link href={`${routeMatch.url}/new`}>
              <Button
                color="primary"
                variant="contained"
                className={classes.control}
                startIcon={<AddIcon/>}
                onClick={() => {}}
              >
                Add New
              </Button>
            </Link>
          </Box>
        }
        title={usersType == 'people'? 'People' : 'Service Users'}
      />
      <Divider/>
      <CardContent className={classes.content}>
        <div className={classes.inner}>
          <UsersTable usersType={usersType}/>
        </div>
      </CardContent>
    </Card>
  );
}

export default UsersManager