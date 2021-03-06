import React, {useEffect, useState} from 'react';
import { Link as RouterLink } from 'react-router-dom';
import clsx from 'clsx';
import PropTypes from 'prop-types';
import { makeStyles } from '@material-ui/styles';
import {AppBar, Toolbar, Badge, Hidden, IconButton, Typography, Button} from '@material-ui/core';
import MenuIcon from '@material-ui/icons/Menu';
import InputIcon from '@material-ui/icons/Input';
import BuildIcon from '@material-ui/icons/Build';
import Grid from '@material-ui/core/Grid';
import {Utils} from '../../../../common/Utils';

const useStyles = makeStyles(theme => ({
  root: {
    boxShadow: 'none'
  },
  flexGrow: {
    flexGrow: 1
  },
  signOutButton: {
    marginLeft: theme.spacing(1)
  },
  logo: {
    color: 'white',
    display: 'flex'
  },
  distributionName: {
    paddingLeft: 10,
    color: 'white',
    fontWeight: 400,
    fontSize: '24px',
    letterSpacing: '-0.06px',
    lineHeight: '28px'
  },
  version: {
    paddingLeft: 10,
    color: 'white',
    fontWeight: 100,
    fontSize: '24px',
    letterSpacing: '-0.06px',
    lineHeight: '28px'
  }
}));

const Topbar = props => {
  const { className, onSidebarOpen, ...rest } = props;

  const classes = useStyles();

  const [distributionInfo, setDistributionInfo] = useState([]);

  useEffect(() => {
    Utils.getDistributionInfo().then(info => {
      localStorage.setItem('distribution', JSON.stringify(info))
      setDistributionInfo(info)
    })
  }, [])

  return (
    <AppBar
      {...rest}
      className={clsx(classes.root, className)}
    >
      <Toolbar>
        <RouterLink to='/'>
          <Grid className={classes.logo}>
            <BuildIcon/>
            { distributionInfo.name ? (
              <>
                <Typography className={classes.distributionName}
                  display='inline'
                >
                  {distributionInfo.name}
                </Typography>
              </>
            ) : null }
          </Grid>
        </RouterLink>
        <div className={classes.flexGrow} />
        <Hidden mdDown>
          <IconButton title='Logout'
            className={classes.signOutButton}
            color='inherit'
            href='/login'
          >
          <InputIcon />
          </IconButton>
        </Hidden>
        <Hidden lgUp>
          <IconButton
            color='inherit'
            onClick={onSidebarOpen}
          >
            <MenuIcon />
          </IconButton>
        </Hidden>
      </Toolbar>
    </AppBar>
  );
};

Topbar.propTypes = {
  className: PropTypes.string,
  onSidebarOpen: PropTypes.func
};

export default Topbar;
