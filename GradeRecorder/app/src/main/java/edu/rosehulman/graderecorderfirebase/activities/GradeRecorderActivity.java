package edu.rosehulman.graderecorderfirebase.activities;

import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.NavigationView;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.Locale;

import edu.rosehulman.graderecorderfirebase.Constants;
import edu.rosehulman.graderecorderfirebase.R;
import edu.rosehulman.graderecorderfirebase.fragments.AssignmentListFragment;
import edu.rosehulman.graderecorderfirebase.fragments.CourseListFragment;
import edu.rosehulman.graderecorderfirebase.fragments.LoginFragment;
import edu.rosehulman.graderecorderfirebase.fragments.OwnerListFragment;
import edu.rosehulman.graderecorderfirebase.fragments.StudentListFragment;
import edu.rosehulman.graderecorderfirebase.models.Assignment;
import edu.rosehulman.graderecorderfirebase.models.Course;
import edu.rosehulman.graderecorderfirebase.models.Owner;
import edu.rosehulman.graderecorderfirebase.utils.SharedPreferencesUtils;
import edu.rosehulman.graderecorderfirebase.utils.Utils;

import edu.rosehulman.rosefire.Rosefire;
import edu.rosehulman.rosefire.RosefireResult;

public class GradeRecorderActivity extends AppCompatActivity implements
        NavigationView.OnNavigationItemSelectedListener,
        LoginFragment.OnLoginListener,
        AssignmentListFragment.OnAssignmentSelectedListener,
        CourseListFragment.OnCourseSelectedListener,
        OwnerListFragment.OnThisOwnerRemovedListener {

    private FloatingActionButton mFab;
    private Toolbar mToolbar;

    private DatabaseReference mFirebaseRef;
    private DatabaseReference mOwnerRef;
    private FirebaseAuth mAuth;
    private FirebaseAuth.AuthStateListener mAuthStateListener;
    private OnCompleteListener mOnCompleteListener;
    private OwnerValueEventListener mOwnerValueEventListener;
    private static final int RC_ROSEFIRE_LOGIN = 1;

    public FloatingActionButton getFab() {
        return mFab;
    }

    public Toolbar getToolbar() {
        return mToolbar;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_grade_recorder);
        mToolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(mToolbar);


        // Skipped during rotation, but Firebase settings should persist.
        if (savedInstanceState == null) {
            initializeFirebase();
        }
        mAuth = FirebaseAuth.getInstance();
        initializeListeners();

        mFab = (FloatingActionButton) findViewById(R.id.fab);

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, mToolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

       mFirebaseRef = FirebaseDatabase.getInstance().getReference();
        Log.d(Constants.TAG, "Finishing oncreate in activity");
    }

    private void initializeFirebase() {
        FirebaseDatabase.getInstance().setPersistenceEnabled(true);
        mFirebaseRef = FirebaseDatabase.getInstance().getReference();
        mFirebaseRef.keepSynced(true);
    }

    private void initializeListeners() {
        mAuthStateListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                Log.d(Constants.TAG, "In activity, authlistener");
                FirebaseUser user = firebaseAuth.getCurrentUser();


                Log.d(Constants.TAG, "Current user: " + user);
                if (user == null) {
                    FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
                    ft.replace(R.id.container, new LoginFragment(), "login");
                    ft.commit();
                } else {
                    SharedPreferencesUtils.setCurrentUser(GradeRecorderActivity.this, user.getUid());
                    Log.d(Constants.TAG, "User is authenticated");
                    mOwnerRef = FirebaseDatabase.getInstance().getReference().child(Constants.OWNERS_PATH).child(user.getUid());
                    // MB: moved from here
                    // TODO: Need to differ, if auth via rosefire or email/password.
                    Log.d(Constants.TAG, " Provider: " + firebaseAuth.getCurrentUser().getProviderId());
                    Log.d(Constants.TAG, " user display name: " + firebaseAuth.getCurrentUser().getDisplayName());
                    Log.d(Constants.TAG, " user email: " + firebaseAuth.getCurrentUser().getEmail());
                    // Currently, if rosefire, email is null. Will be fixed in next version.
                    if (firebaseAuth.getCurrentUser().getEmail() != null) {
                        // Email/password.
                        if (mOwnerValueEventListener != null) {
                            mOwnerRef.removeEventListener(mOwnerValueEventListener);
                        }
                        Log.d(Constants.TAG, "Adding OwnerValueListener for " + mOwnerRef.toString());
                        mOwnerValueEventListener = new OwnerValueEventListener();
                        mOwnerRef.addValueEventListener(mOwnerValueEventListener);
                        // TODO: This isn't triggering the dialog, as it should.
                    } else {
                        // Rosefire: Done
                        // MB: moved from above
                        mOwnerRef.child(Owner.USERNAME).setValue(user.getUid());
                        Log.d(Constants.TAG, "Rosefire worked. UID = " + user.getUid());
                        onLoginComplete(user.getUid());
                    }
                }
            }
        };

        mOnCompleteListener = new OnCompleteListener<AuthResult>() {
            @Override
            public void onComplete(@NonNull Task<AuthResult> task) {
                Log.d(Constants.TAG, "In activity, oncompletelistener");
                if (!task.isSuccessful()) {
                    showLoginError("Authentication failed.");
                }
            }
        };
    }

    private void showLoginError(String message) {
        LoginFragment loginFragment = (LoginFragment) getSupportFragmentManager().findFragmentByTag("login");
        loginFragment.onLoginError(message);
    }

    @Override
    public void onRosefireLogin() {
        Intent signInIntent = Rosefire.getSignInIntent(this, Constants.ROSEFIRE_REGISTRY_TOKEN);
        startActivityForResult(signInIntent, RC_ROSEFIRE_LOGIN);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == RC_ROSEFIRE_LOGIN) {
            RosefireResult result = Rosefire.getSignInResultFromIntent(data);
            if (result.isSuccessful()) {
                firebaseAuthWithRosefire(result);
            } else {
                showLoginError("Rosefire authentication failed.");
            }
        }
    }

    private void firebaseAuthWithRosefire(RosefireResult result) {
        mAuth.signInWithCustomToken(result.getToken())
                .addOnCompleteListener(mOnCompleteListener);
    }

        @Override
    public void onStart() {
        super.onStart();
        mAuth.addAuthStateListener(mAuthStateListener);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mAuthStateListener != null) {
            mAuth.removeAuthStateListener(mAuthStateListener);
        }
        // Was in fragment onDetach
        if (mOwnerValueEventListener != null) {
            mOwnerRef.removeEventListener(mOwnerValueEventListener);
        }
        mOwnerValueEventListener = null;
    }

    public void onLoginComplete(String uid) {
        Log.d(Constants.TAG, "User is authenticated");

        SharedPreferencesUtils.setCurrentUser(this, uid);

        // Check if they have a current course
        String currentCourseKey = SharedPreferencesUtils.getCurrentCourseKey(this);
        Fragment switchTo;
        if (currentCourseKey == null || currentCourseKey.isEmpty()) {
            switchTo = new CourseListFragment();
        } else {
            switchTo = AssignmentListFragment.newInstance(currentCourseKey);
        }
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        ft.replace(R.id.container, switchTo);
        ft.commit();
    }

    @Override
    public void onLogin(String email, String password) {
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, mOnCompleteListener);
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        String currentCourseKey = SharedPreferencesUtils.getCurrentCourseKey(this);
        int id = item.getItemId();
        Fragment switchTo = null;
        String tag = "";

// TODO: May be useful if I implement return to the chosen fragment after choosing a course.
        if (id == R.id.nav_sign_out) {
            Utils.signOut(this);
            switchTo = new LoginFragment();
            tag = "login";
        } else if (id == R.id.nav_courses || currentCourseKey == null) {
            switchTo = new CourseListFragment();
            tag = "courses";
        } else if (id == R.id.nav_assignments) {
            switchTo = AssignmentListFragment.newInstance(currentCourseKey);
            tag = "assignments";
        } else if (id == R.id.nav_students) {
            switchTo = new StudentListFragment();
            tag = "students";
        } else if (id == R.id.nav_owners) {
            switchTo = new OwnerListFragment();
            tag = "owners";
        }

        if (switchTo != null) {
            FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            ft.replace(R.id.container, switchTo, tag);
            ft.commit();
        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    public void onAssignmentSelected(Assignment assignment) {
        // TODO: go to grade entry fragment


    }

    @Override
    public void onCourseSelected(Course selectedCourse) {
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        ft.replace(R.id.container, AssignmentListFragment.newInstance(selectedCourse.getKey()));
        ft.addToBackStack("course_fragment");
        ft.commit();
    }

    @Override
    public void onThisOwnerRemoved() {
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        ft.replace(R.id.container, new CourseListFragment());
        ft.commit();
    }

    class OwnerValueEventListener implements ValueEventListener {
        @Override
        public void onDataChange(DataSnapshot dataSnapshot) {
            String username = (String) dataSnapshot.child(Owner.USERNAME).getValue();
            Log.d(Constants.TAG, "Rose username in LoginActivity: " + username);
            if (username == null) {
                showUsernameDialog();
            } else {
                if (mOwnerValueEventListener != null) {
                    mOwnerRef.removeEventListener(mOwnerValueEventListener);
                }
                // TODO: check if this is correct
                String currentUser = SharedPreferencesUtils.getCurrentUser(GradeRecorderActivity.this);
                Log.d(Constants.TAG, String.format(Locale.US, "Sharedprefs current user: [%s]\n", currentUser));
                onLoginComplete(currentUser);
            }
        }

        @Override
        public void onCancelled(DatabaseError firebaseError) {
            Log.d(Constants.TAG, "OwnerValueListener cancelled: " + firebaseError);
        }
    }

    @SuppressLint("InflateParams")
    private void showUsernameDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Enter Rose username");
        View view = getLayoutInflater().inflate(R.layout.dialog_get_rose_username, null);
        builder.setView(view);
        final EditText roseUsernameEditText = (EditText) view
                .findViewById(R.id.dialog_get_rose_username);
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String roseUsername = roseUsernameEditText.getText().toString();
                        String uid = SharedPreferencesUtils.getCurrentUser(GradeRecorderActivity.this);
                        mOwnerRef = FirebaseDatabase.getInstance().getReference().child(Constants.OWNERS_PATH).child(uid);
                        mOwnerRef.child(Owner.USERNAME).setValue(roseUsername);
                        onLoginComplete(uid);
                    }
                }
        );
        builder.create().show();
    }
}
